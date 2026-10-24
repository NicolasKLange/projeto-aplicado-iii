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

## 4. Persistência e idempotência

A unicidade do hash (RN-20) não pode depender de um `GetItem` antes da gravação, porque dois eventos simultâneos do S3 passariam os dois pela checagem. No DynamoDB, que não tem `UNIQUE`, ela vira um item-sentinela `HASH#<sha256>` gravado na mesma transação (`TransactWriteItems`) que o novo estado do envio, com `attribute_not_exists`: se o hash já existe, a transação inteira falha e nada fica pela metade. A porta `RepositorioCalibracoes` (`nucleo`, Java puro) expressa isso com três desfechos: `Registrada`, `Retomada` (a sentinela já pertence ao mesmo envio, ou seja, o evento do S3 chegou de novo) e `Duplicada` (pertence a outro envio, que passa a `DUPLICADA` com ponteiro para o original).

O id do upload é o id da calibração: a `fn-api` grava o envio em `PROCESSANDO` antes de o arquivo existir, e a tela consulta esse id até o estado mudar. São cinco estados (`PROCESSANDO`, `PROCESSADA`, `REJEITADA`, `DUPLICADA`, `EMITIDA`), cada transição condicional ao estado anterior. A planilha rejeitada por regra de negócio também grava a sentinela; reenviada, é encerrada pelo hash, sem abrir o Excel, e a tela segue o ponteiro até os erros originais. Falha técnica não grava sentinela, para não bloquear o arquivo por um erro passageiro. Limitação: se o contrato da planilha mudar, o hash já rejeitado continua preso.

Divergências em relação à AV02 §3.2.2 (D-021, `docs/av04/modelo-dados.md`): sem item `ARQ#` (o nome original do arquivo não é gravado, só `sha256` e tamanho), resultado embutido no item principal, estados reduzidos, GSI2 por usuário em vez de por estado (a fila de aprovação ficou fora, D-018) e sem GSI1. O registro Java recusa combinações incoerentes, como `REJEITADA` com resultado. Teste: adaptador em memória e 11 casos (mesmo hash duas vezes, hash diferente, evento repetido, rejeitada sem dado de negócio, reenvio da planilha rejeitada, emissão e unicidade do token). A prova contra o DynamoDB real fica para a T06, quando a conta AWS estiver liberada.

## 5. Plataforma Web

**Certificado gerado (T05).** A classe `GeradorCertificado` (`funcoes`, Apache PDFBox 3.0.8) recebe o cálculo pronto do núcleo e os metadados (`DadosCertificado`) e devolve `byte[]`. O PDF traz identificador, data e hora em America/Sao_Paulo, SHA-256 da planilha de origem, "Gerado por: <usuário autenticado>" (D-003: a autoria é a autenticação, com o hash garantindo a integridade), a tabela de pontos (leituras, referência, média, erro, LEA e status com os enums literais), o parecer em destaque, o token e a URL de verificação, esta também em QR Code (ZXing `core` 3.5.4; a matriz é desenhada como retângulos no PDFBox, sem o módulo `javase`). Números em 5 casas só na exibição; leituras como lidas. O nome de exibição existe só no PDF, e caracteres fora do WinAnsi viram `?` em vez de derrubar a emissão. Rodapé: "Documento gerado por PoC acadêmica (UniSENAI, Projeto Aplicado III) — sem validade metrológica".

Divergência de PV-14: o layout é simplificado, não reproduz o certificado da Schulz. Teste: gera o PDF de `ok-reprovado.xlsx` e confere com `PDFTextStripper` o parecer `REPROVADO`, o hash, `FORA_DA_TOLERANCIA`, o usuário e o token; outro caso cobre `APROVADO`. Evidência: `docs/av04/evidencias/certificado-ok-reprovado.pdf` (planilha sintética). Print do PDF aberto: `docs/av04/evidencias/tela/relatorio-pdf.png`.

## 6. Roteiro de deploy na AWS

**Pré-requisitos (T02, 24/10/2026).** AWS CLI v2.36.40, SAM CLI 1.170.0, Node v24.11.1 / npm 11.6.2 e JDK 21 (Temurin 21.0.10) instalados e conferidos. Credenciais só no perfil `pa3` do AWS CLI, com `AdministratorAccess` no usuário IAM da conta de estudo; `.gitignore` com `.aws-sam/` e `.claude/commands/` versionável.

**Bloqueio encontrado.** Leituras de baixo risco (`lambda list-functions`, `dynamodb list-tables`, `cloudformation list-stacks`, `cognito-idp list-user-pools`) com o perfil `pa3` foram negadas em `sa-east-1` e `us-east-1` com *explicit deny in a service control policy* e funcionaram em `us-east-2`. O `describe-organization` mostrou que a conta é membro de uma AWS Organization com SCP ativa; o MFA da raiz e do usuário IAM não pôde ser configurado (IAM negado pela SCP; página de credenciais indisponível). Depois disso a conta original foi suspensa e uma segunda conta não obteve o plano gratuito, por cruzamento de dados de cadastro. Caso aberto no AWS Support; o deploy fica pendente da resposta. O back-end e o front-end são desenvolvidos e testados localmente, com a região como parâmetro dos templates.

## 7. Segurança e LGPD

## 8. Custo e Free Tier

**Monitoramento (T02).** Dois orçamentos no AWS Budgets: A-1 *Zero spend budget* e A-2 de US$ 1,00/mês, ambos com alerta por e-mail (prints em `docs/av04/evidencias/tela/`, sem número de conta). Painel do plano gratuito em 24/10/2026: US$ 120,00 de créditos restantes e 129 dias, sem cobrança. Limitação: a conta foi suspensa depois dessa leitura; ver §6.

## 9. Evidências

## 10. Matriz de Rastreabilidade de Requisitos

## 11. Limitações e roadmap

## 12. Premissas e pontos a validar

- **[PREMISSA]** O PDF do roteiro da AV04 pula da seção 2 para a 5. Perguntas ao professor enviadas em 24/10/2026 (seções 3 e 4 trazem requisitos? Plataforma Web com página pública de verificação por QR Code; vídeo "não listado" no YouTube). Sem resposta até a data. Na AV03 o professor já havia enviado arquivo com numeração errada, confirmado por mensagem, sem conteúdo nas seções faltantes; seguimos assumindo que não há requisitos além dos presentes no documento.
- **[PREMISSA]** O plano gratuito do CloudFront aceita OAC? A página de preços não informa; confirmar na criação da distribuição (T10).

## Referências
- **[PONTO A VALIDAR]** Região: a SCP da conta só permitiu `us-east-2`; a D-006 e a AV02 assumem `sa-east-1` (LGPD: sem transferência internacional). Decisão (D-021) adiada até a resposta do suporte AWS.
- **[PONTO A VALIDAR]** MFA da raiz e do usuário IAM não configurado por restrição da conta; confirmar com o suporte.
