# AV04 — Plataforma Web e deploy em nuvem (fonte do relatório)

<!-- Cada tarefa acrescenta o seu trecho na seção indicada em "Relatório" no guia. -->

## 1. Introdução e vínculo com AV01–AV03

## 2. Decisões da entrega

As decisões D-015 a D-020 (`docs/decisoes.md`, 24/10/2026) fecham o escopo da AV04 antes do código:

- **D-015, Plataforma Web:** o público é o engenheiro no laboratório; a validação por QR Code do roteiro é uma página pública responsiva aberta pela câmera do celular (RF-16 da AV01).
- **D-016, SPA em S3 privado + CloudFront com OAC:** HTTPS para a tela de login; o plano gratuito oferece 1 milhão de requisições e 100 GB/mês, sem cobrança de excedente (https://aws.amazon.com/cloudfront/pricing/, 24/10/2026). Descartados: Amplify Hosting e S3 com website público.
- **D-017, Maven multimódulo (`nucleo`, `app-local`, `funcoes`):** o `nucleo` não declara o Spring, então o compilador garante a D-012; as funções Lambda levam só o núcleo e o SDK da AWS.
- **D-018, recorte:** passos 1–7 e 10 do fluxo da AV02; ficam no roadmap TOTP, aprovação/RN-12, numeração com Object Lock e incerteza (R-46 a R-50). Mudanças técnicas: PUT pré-assinado no lugar do POST e rejeição registrada no DynamoDB no lugar de `quarentena/`.
- **D-019, bibliotecas:** SDK da AWS v2, eventos do Lambda, PDFBox 3, ZXing `core`, React + Vite e SAM CLI.
- **D-020, runtime `java21` com bytecode 17:** o `java17` (AL2) vai até 30/06/2027; `java21` (AL2023) até 30/06/2029 (https://docs.aws.amazon.com/lambda/latest/dg/lambda-runtimes.html, 24/10/2026).

## 3. Arquitetura implementada

**Estrutura de módulos (D-017).** O projeto é um Maven multimódulo: `nucleo` (cálculo metrológico e leitura da planilha, só com o Apache POI), `app-local` (tela e API da AV03, com Spring Boot) e `funcoes` (Lambdas, a partir da T05). O POM pai mantém o `spring-boot-starter-parent` para herdar a gestão de versões, mas só o `app-local` declara dependência do Spring. O cálculo de SHA-256 em streaming (`Medidor`) foi para o `nucleo`, porque a `fn-ingestao` também precisa dele; o log continua no `ProcessamentoService`.

**Prova da D-012.** O `pom.xml` do `nucleo` não tem Spring nem AWS, então um `import` de framework ali não compila; e `grep -r "springframework" nucleo/` devolve vazio. Os 71 testes da AV03 continuam passando (56 no `nucleo`, 15 no `app-local`), sem alteração de código de teste. As planilhas sintéticas ficam só no `nucleo` e o `app-local` as consome pelo *test-jar*, sem duplicar arquivos.

**`fn-ingestao` (T06).** O handler recebe o evento do S3, extrai `sub` e `uploadId` da chave `uploads/<sub>/<uploadId>.xlsx` e lê o objeto inteiro para memória (teto de 5 MB, D-023). Calcula o SHA-256, consulta o dono do hash e, se a planilha é nova, lê, calcula e grava pela porta `RepositorioCalibracoes`. Planilha inválida grava `REJEITADA` e duplicata grava `DUPLICADA`, ambas terminando com sucesso e sem `calibracaoId` no retorno, para que a `fn-certificado` não seja acionada; falha inesperada (chave fora do padrão, envio inexistente, S3 ou DynamoDB indisponível) lança exceção e o evento vai para a DLQ. O log traz só `uploadId`, SHA-256, tamanho e resultado, e um teste confirma que `sub` e chave não aparecem. O adaptador DynamoDB grava cada transição com sentinela em uma `TransactWriteItems` de dois itens, e uma função pura (`DecisorCancelamento`) separa evento repetido, duplicata e erro transitório a partir dos motivos do cancelamento. Valores metrológicos são gravados como texto para preservar a escala 10. Os 35 testes da `funcoes` usam S3 e DynamoDB falsos; a prova das condições no DynamoDB real fica para a T09.

## 4. Persistência e idempotência

A unicidade do hash (RN-20) não pode depender de um `GetItem` antes da gravação, porque dois eventos simultâneos do S3 passariam os dois pela checagem. No DynamoDB, que não tem `UNIQUE`, ela vira um item-sentinela `HASH#<sha256>` gravado na mesma transação (`TransactWriteItems`) que o novo estado do envio, com `attribute_not_exists`: se o hash já existe, a transação inteira falha e nada fica pela metade. A porta `RepositorioCalibracoes` (`nucleo`, Java puro) expressa isso com três desfechos: `Registrada`, `Retomada` (a sentinela já pertence ao mesmo envio, ou seja, o evento do S3 chegou de novo) e `Duplicada` (pertence a outro envio, que passa a `DUPLICADA` com ponteiro para o original).

O id do upload é o id da calibração: a `fn-api` grava o envio em `PROCESSANDO` antes de o arquivo existir, e a tela consulta esse id até o estado mudar. São cinco estados (`PROCESSANDO`, `PROCESSADA`, `REJEITADA`, `DUPLICADA`, `EMITIDA`), cada transição condicional ao estado anterior. A planilha rejeitada por regra de negócio também grava a sentinela; reenviada, é encerrada pelo hash, sem abrir o Excel, e a tela segue o ponteiro até os erros originais. Falha técnica não grava sentinela, para não bloquear o arquivo por um erro passageiro. Limitação: se o contrato da planilha mudar, o hash já rejeitado continua preso.

Divergências em relação à AV02 §3.2.2 (D-021, `docs/av04/modelo-dados.md`): sem item `ARQ#` (o nome original do arquivo não é gravado, só `sha256` e tamanho), resultado embutido no item principal, estados reduzidos, GSI2 por usuário em vez de por estado (a fila de aprovação ficou fora, D-018) e sem GSI1. O registro Java recusa combinações incoerentes, como `REJEITADA` com resultado. Teste: adaptador em memória e 11 casos (mesmo hash duas vezes, hash diferente, evento repetido, rejeitada sem dado de negócio, reenvio da planilha rejeitada, emissão e unicidade do token). A prova contra o DynamoDB real fica para a T06, quando a conta AWS estiver liberada.

## 5. Plataforma Web

**Certificado gerado (T05).** A classe `GeradorCertificado` (`funcoes`, Apache PDFBox 3.0.8) recebe o cálculo pronto do núcleo e os metadados (`DadosCertificado`) e devolve `byte[]`. O PDF traz identificador, data e hora em America/Sao_Paulo, SHA-256 da planilha de origem, "Gerado por: <usuário autenticado>" (D-003: a autoria é a autenticação, com o hash garantindo a integridade), a tabela de pontos (leituras, referência, média, erro, LEA e status com os enums literais), o parecer em destaque, o token e a URL de verificação, esta também em QR Code (ZXing `core` 3.5.4; a matriz é desenhada como retângulos no PDFBox, sem o módulo `javase`). Números em 5 casas só na exibição; leituras como lidas. O nome de exibição existe só no PDF, e caracteres fora do WinAnsi viram `?` em vez de derrubar a emissão. Rodapé: "Documento gerado por PoC acadêmica (UniSENAI, Projeto Aplicado III) — sem validade metrológica".

Divergência de PV-14: o layout é simplificado, não reproduz o certificado da Schulz. Teste: gera o PDF de `ok-reprovado.xlsx` e confere com `PDFTextStripper` o parecer `REPROVADO`, o hash, `FORA_DA_TOLERANCIA`, o usuário e o token; outro caso cobre `APROVADO`. Evidência: `docs/av04/evidencias/certificado-ok-reprovado.pdf` (planilha sintética). Print do PDF aberto: `docs/av04/evidencias/tela/relatorio-pdf.png`.

## 6. Roteiro de deploy na AWS

**Pré-requisitos (T02, 24 e 25/10/2026).** AWS CLI v2.36.40, SAM CLI 1.170.0, Node v24.11.1 / npm 11.6.2 e JDK 21 (Temurin 21.0.10) instalados e conferidos. Na conta definitiva, credenciais só no perfil `pa3` do AWS CLI, região `us-east-2`, com o usuário IAM `pa3-deploy` (`AdministratorAccess`, sem senha de console, só access key). Verificação em 25/10/2026: `aws sts get-caller-identity --profile pa3` devolve o ARN `user/pa3-deploy` e `aws dynamodb list-tables` em `us-east-2` devolve lista vazia, sem negação da SCP. `.gitignore` com `.aws-sam/` e `.claude/commands/` versionável.

**Bloqueio encontrado.** Leituras de baixo risco (`lambda list-functions`, `dynamodb list-tables`, `cloudformation list-stacks`, `cognito-idp list-user-pools`) com o perfil `pa3` foram negadas em `sa-east-1` e `us-east-1` com *explicit deny in a service control policy* e funcionaram em `us-east-2`. O `describe-organization` mostrou que a conta é membro de uma AWS Organization com SCP ativa; o MFA da raiz e do usuário IAM não pôde ser configurado (IAM negado pela SCP; página de credenciais indisponível). Depois disso a conta original foi suspensa e uma segunda conta não obteve o plano gratuito, por cruzamento de dados de cadastro. Caso aberto no AWS Support.

**Conta definitiva e região (25/10/2026, D-022).** Uma conta nova obteve o plano gratuito (US$ 100 em créditos, válido até 24/04/2027). Ela está na "nova experiência AWS", em que cada projeto é uma conta-membro de uma Organization gerenciada pela própria AWS; a mesma SCP só libera serviços regionais em `us-east-2`, o que explica o bloqueio anterior. No console, Lambda, S3, DynamoDB, Cognito, API Gateway e CloudFormation abrem em `us-east-2`; CloudFront e Budgets, globais, também. O deploy passa a usar `us-east-2`. O login no console é pelo AWS Builder ID, que assume um papel gerenciado na conta, com MFA no próprio Builder ID, e o acesso programático é um usuário IAM dedicado (`pa3-deploy`) no perfil `pa3`.

## 7. Segurança e LGPD

**Região e transferência internacional (D-022).** Com `us-east-2`, os dados processados ficam nos EUA, o que configura transferência internacional (LGPD, art. 33). A PoC só processa planilhas sintéticas ou sanitizadas, sem dado pessoal, então a transferência não alcança dado pessoal. Em produção, a recomendação é `sa-east-1` em conta no plano pago.

## 8. Custo e Free Tier

**Monitoramento (T02).** Dois orçamentos no AWS Budgets da conta definitiva: A-1 *Zero spend budget* (alerta quando o custo real passa de US$ 0,01) e A-2 de US$ 1,00/mês (custo real acima de 85% e de 100%, previsto acima de 100%). O e-mail direto do Budgets não pôde ser verificado, porque o serviço `notifications-contacts` é negado pela SCP do plano; os quatro alertas publicam num tópico SNS em `us-east-2` com assinatura por e-mail confirmada (D-022), dentro da cota Always Free do SNS. Print da lista de orçamentos em `docs/av04/evidencias/tela/budgets-conta-nova.png`, sem número de conta. Painel do plano gratuito em 25/10/2026: US$ 100,00 de créditos restantes, 183 dias (até 24/04/2027), US$ 0,00 no mês, sem cobrança. A previsão de fim de mês aparece como "Data unavailable" por falta de histórico de uso, o que é esperado em conta nova.

## 9. Evidências

## 10. Matriz de Rastreabilidade de Requisitos

## 11. Limitações e roadmap

## 12. Premissas e pontos a validar

- **[PREMISSA]** O PDF do roteiro da AV04 pula da seção 2 para a 5. Perguntas ao professor enviadas em 24/10/2026 (seções 3 e 4 trazem requisitos? Plataforma Web com página pública de verificação por QR Code; vídeo "não listado" no YouTube). Sem resposta até a data. Na AV03 o professor já havia enviado arquivo com numeração errada, confirmado por mensagem, sem conteúdo nas seções faltantes; seguimos assumindo que não há requisitos além dos presentes no documento.
- **[PREMISSA]** O plano gratuito do CloudFront aceita OAC? A página de preços não informa; confirmar na criação da distribuição (T10).
- **[RESOLVIDO 25/10]** Região: a SCP do plano gratuito só libera `us-east-2`; a AV02 assumia `sa-east-1`. Decidido na D-022, com o efeito de LGPD tratado na §7.
- **[RESOLVIDO 25/10]** MFA do login humano: ativo no AWS Builder ID, com dois dispositivos (WebAuthn e TOTP). Evidência: `docs/av04/evidencias/tela/mfa-tarjado.png` (e-mails e IP tarjados).
- **[PREMISSA]** O usuário IAM `pa3-deploy` não tem MFA: não tem senha de console e só usa access key, guardada apenas no perfil local `pa3`. A chave é desativada depois da nota.

## Referências
