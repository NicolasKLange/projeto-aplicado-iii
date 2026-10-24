# Registro de decisões

Decisões que fecham escopo ou arquitetura. Uma linha por decisão, com data e origem.
Decisão revista não é apagada: recebe status `Substituída` e aponta para a que a sucede.

## D-001 — Provedor de nuvem: AWS
**Data:** 17/09/2026 · **Origem:** grupo · **Status:** Vigente

O Always Free da AWS não expira em 12 meses nos serviços que sustentam o pipeline
(Lambda 1M req/mês, DynamoDB 25 GB, SQS, SNS, CloudWatch Alarms), o que permite sustentar
custo R$ 0,00 por todo o semestre sem depender de cota promocional de 12 meses.
O grupo também tem familiaridade prévia com o ambiente.

## D-002 — Ingestão: upload de planilha, sem integração direta
**Data:** 17/09/2026 · **Origem:** Schulz · **Status:** Vigente

Sem integração com Calypso ou LightLab — nem API, nem banco, nem conector de equipamento.
O operador faz upload da planilha Excel/CSV sanitizada no storage; o evento dispara a função
serverless que lê o arquivo, grava no banco e gera o PDF.
Consequência: a ingestão por arquivo deixa de ser contingência e passa a ser a arquitetura.

## D-003 — Assinatura: sem ICP-Brasil na PoC
**Data:** 17/09/2026 · **Origem:** Schulz · **Status:** Vigente

Fluxo interno da Schulz. Usar apenas autenticação de usuário como mecanismo de autoria do
PDF gerado, com hash SHA-256 garantindo integridade. ICP-Brasil fica no roadmap.

## D-004 — Retenção: 5 anos (ISO/IEC 17025)
**Data:** 17/09/2026 · **Origem:** Schulz · **Status:** Vigente

Indicar regras de ciclo de vida no storage: Standard nos primeiros 12 meses, transição para
classe de arquivamento depois, expiração em 5 anos, com Object Lock impedindo exclusão antes
do prazo.

## D-005 — Escopo do MVP: UC-01 a UC-03
**Data:** 17/09/2026 · **Origem:** grupo · **Status:** Vigente

Importar (UC-01), calcular (UC-02) e emitir (UC-03). É a espinha dorsal que resolve as seis
anomalias críticas da AV01. UC-04 a UC-08 e os requisitos RF-19 a RF-22 ficam no roadmap.

## D-006 — Conta dedicada no plano gratuito, região sa-east-1
**Data:** 02/10/2026 · **Origem:** grupo · **Status:** Vigente

Desde 15/07/2025 o Free Tier da AWS para contas novas é um plano gratuito com créditos
(US$ 100 a 200, 6 meses), que não gera fatura; as cotas Always Free continuam valendo.
As ofertas de 12 meses (S3 5 GB, API Gateway 1 milhão) só existem no programa antigo e já
venceram para qualquer conta anterior a essa data. A PoC roda em conta nova e dedicada, em
São Paulo, e não é convertida em plano pago sem decisão registrada aqui.

## D-007 — Banco de dados: DynamoDB em modo provisionado
**Data:** 02/10/2026 · **Origem:** grupo · **Status:** Vigente

Único banco gerenciado com cota permanente (25 GB, 25 RCU, 25 WCU — só no modo provisionado).
Duas tabelas: `calibracoes` (tabela única, 2 GSIs) e `auditoria` (somente inserção).
Custo assumido: integridade referencial e unicidade passam para a aplicação (transações e
itens-sentinela). RDS e Aurora descartados por consumo contínuo de créditos.

## D-008 — Encadeamento por destino do Lambda, SQS apenas como fila de falhas
**Data:** 02/10/2026 · **Origem:** grupo · **Status:** Vigente

Fila como gatilho mantém consultas permanentes ao SQS mesmo vazia. O pipeline encadeia
`fn-ingestao` → `fn-certificado` por destino de sucesso; o SQS recebe só eventos que falharam.

## D-009 — Criptografia em repouso com SSE-S3 na PoC
**Data:** 02/10/2026 · **Origem:** grupo · **Status:** Vigente

Chave própria no KMS custa US$ 1/mês e não tem cota gratuita. PoC usa SSE-S3 e a chave
padrão do DynamoDB; recomendação para produção: SSE-KMS com chave do cliente.

## D-010 — Aprovação do signatário entre a minuta e a emissão
**Data:** 02/10/2026 · **Origem:** grupo · **Status:** A validar com a Schulz (PV-21)

O pipeline gera a minuta automaticamente; a emissão definitiva ocorre após aprovação de um
signatário diferente do técnico (RN-12). Se a empresa quiser emissão totalmente automática,
os dois passos se fundem por configuração.

## D-011 — Stack do MVP: Java 17 + Spring Boot 4.1 + Maven
**Data:** 10/10/2026 · **Origem:** autor · **Status:** Vigente

Python e Node também atenderiam a esta solução. A escolha por Java se deve à familiaridade
do autor com a stack, o que reduz o risco no prazo da entrega, e ao `BigDecimal` nativo, que
dá controle total sobre a precisão e o arredondamento dos cálculos metrológicos; no Node,
seria necessária uma biblioteca adicional para decimais. O Spring Boot fornece, sem
dependências extras, upload de arquivos, API REST e a página estática da interface. O Maven,
padrão do Spring Initializr, simplifica o gerenciamento de dependências, como o Apache POI —
única biblioteca externa fora do ecossistema Spring, necessária para ler arquivos `.xlsx`.
O Spring Boot é pesado para execução em AWS Lambda; esse custo é tratado na D-012.
A interface de validação será simples, em HTML + JS: como há uma etapa específica para a
interface (AV04), esta entrega usa o mínimo possível de dependências e mantém a separação
de responsabilidades entre as entregas. Para arquivos muito extensos, com milhões de linhas,
essa arquitetura deixaria de ser adequada; para o volume do projeto, ela é suficiente.

Emenda 10/10: Spring Boot 4.1 (a linha 3.x saiu do suporte gratuito em 30/06/2026)

## D-012 — Núcleo de cálculo independente do Spring
**Data:** 10/10/2026 · **Origem:** autor · **Status:** Vigente

O núcleo — leitura da planilha e cálculo metrológico — é escrito em Java puro, sem importar
nada do Spring. Rodar sem o framework evita carregar contexto, anotações e injeção de
dependências, o que reduz a lentidão do cold start quando o mesmo núcleo for executado na
`fn-ingestao` (AV02). AWS Lambda SnapStart e compilação nativa com GraalVM foram
consideradas e descartadas: acrescentam complexidade sem ganho real para o volume do
projeto. O resultado é um núcleo enxuto, só com o essencial, envolvido por uma camada web
mais robusta. O meio do processo é o mesmo nos dois cenários; mudam as pontas. Na entrada,
o arquivo chega por requisição HTTP no Spring Boot ou pelo evento do S3 na Lambda. Na saída,
o Spring devolve o resultado como resposta HTTP para exibição no navegador, enquanto na
Lambda um adaptador de saída grava os dados no DynamoDB e aciona a `fn-certificado`.

## D-013 — Limite de descompactação do .xlsx no código (ZipSecureFile)
**Data:** 11/10/2026 · **Origem:** autor · **Status:** Vigente

O `.xlsx` é um zip; o POI carrega a aba inteira na memória (DOM). Os padrões do `ZipSecureFile`
recusam taxa de compressão acima de 100×, mas aceitam até 4 GB por entrada: com a R01 (5 MB),
uma entrada ainda poderia chegar a ~500 MB de XML. O `LeitorPlanilhaPoi` fixa, num bloco
`static`, 50 MB por entrada. Medição: a maior planilha que o contrato aceita (10 mil linhas,
5 leituras) gera ~4,2 MB de XML na aba, mais de 10× abaixo do limite. A configuração é global
na JVM, sem conflito porque só o leitor usa o POI. Arquivo acima do limite cai na R02 (o POI
lança `IOException`, sem tipo próprio). Registrar só no relatório foi descartado: documenta o
risco, mas não protege o heap.

## D-014 — Persistência do processamento adiada para a AV04
**Data:** 12/10/2026 · **Origem:** autor · **Status:** Vigente

O roteiro da AV03 pede ingestão, cálculo, parecer, interface e evidências; não pede gravação do
resultado. Persistir agora exigiria um banco local (H2) que não é o da arquitetura: a D-007 define
DynamoDB, em que a unicidade fica na aplicação (itens-sentinela), e não numa restrição `UNIQUE`.
A idempotência por hash (RN-20) seria implementada duas vezes, com mecanismos diferentes. Na AV04,
com o back-end pronto, a persistência entra direto no desenho da D-007. Consequência: na AV03, cada
requisição processa um arquivo de ponta a ponta (recebe, valida, calcula e responde) e nada é gravado
além da linha de log com SHA-256 e tamanho (T15); o relatório declara a limitação. A linha de base
das evidências da T18 continua válida, sem recoleta.

## D-015 — Plataforma Web, não mobile
**Data:** 24/10/2026 · **Origem:** autor · **Status:** Vigente

O público principal é o engenheiro no laboratório: envia a planilha, revisa o resultado e baixa o
PDF, tarefas de tela grande e teclado. A validação por QR Code pedida no roteiro da AV04 entra como
página pública e responsiva, aberta pela câmera do celular a partir do QR impresso no certificado
(RF-16 da AV01). App mobile nativo foi descartado: dobraria o trabalho de interface sem atender a um
uso que a página pública já cobre. Premissa: o professor ainda não respondeu à pergunta enviada na
T00 sobre essa escolha (`[PREMISSA]` no relatório).

## D-016 — Hospedagem da SPA: S3 privado + CloudFront com OAC
**Data:** 24/10/2026 · **Origem:** autor · **Status:** Vigente

A AV02 não definiu onde fica a interface. O bucket S3 fica privado e o CloudFront o serve com OAC,
dando HTTPS, necessário para uma tela de login (Cognito). O plano gratuito do CloudFront inclui
1 milhão de requisições e 100 GB de transferência por mês, sem cobrança de excedente, folga ampla
para o volume do projeto e coerente com o custo R$ 0,00 (D-006, D-009). Fonte:
https://aws.amazon.com/cloudfront/pricing/ (consultada em 24/10/2026). A página não informa se o
plano é permanente nem se aceita OAC: ponto a confirmar na T10. Descartados: Amplify Hosting, por
depender de cota de 12 meses do programa antigo (D-006); e S3 com *website hosting* público, que
não oferece HTTPS e expõe o bucket.

## D-017 — Maven multimódulo: `nucleo`, `app-local` e `funcoes`
**Data:** 24/10/2026 · **Origem:** autor · **Status:** Vigente

O Lambda não pode carregar o Spring (D-012). Separar o projeto em `nucleo`, `app-local` e `funcoes`
é o que prova a D-012 na prática: o `pom.xml` do `nucleo` não declara o Spring, então um `import` do
framework ali quebra o build, e a regra deixa de depender de disciplina. O JAR sombreado de `funcoes`
leva só o núcleo e o SDK da AWS, sem a bagagem do framework, o que mantém o cold start enxuto. O mesmo
núcleo atende as duas pontas, com os mesmos testes de cálculo: entrada HTTP no `app-local`, evento do
S3 em `funcoes`. Alternativa descartada: módulo único com pacotes separados, porque o compilador não
impediria a dependência do Spring.

## D-018 — Recorte da AV04 e divergências em relação à AV02
**Data:** 24/10/2026 · **Origem:** autor · **Status:** Vigente

A entrega cobre os passos 1–7 e 10 do fluxo da AV02 (§1.1), com emissão automática (a D-010 já
previa fundir minuta e emissão por configuração). O escopo foi dimensionado para um autor, não para o
grupo previsto na AV02, e o prazo é curto: itens opcionais ameaçariam o fechamento de uma linha de base
segura. Ficam fora e vão para o roadmap (`AV4_fonte.md` §11): TOTP no Cognito; aprovação do signatário
e RN-12 (passo 8); numeração sequencial e Object Lock (passo 9), de modo que a imutabilidade do PDF não
é garantida pela plataforma; e incerteza, k e veff (R-46 a R-50). Duas mudanças são técnicas, não de
prazo: a URL pré-assinada **PUT** com `Content-Length` assinado substitui o formulário POST, pois o SDK
Java v2 não gera POST pré-assinado (a confirmar na T07), mantendo o limite de tamanho; e o arquivo
inválido não vai para `quarentena/`, a rejeição fica no DynamoDB com os erros (linha, coluna, regra),
visível na tela. Mantidos: validação de entrada, idempotência por hash (RN-20), autenticação de usuário
no PDF, retenção de 5 anos e custo R$ 0,00.

## D-019 — Bibliotecas externas da AV04
**Data:** 24/10/2026 · **Origem:** autor · **Status:** Vigente

Além do Apache POI (D-011), a entrega usa: `aws-lambda-java-core` e `aws-lambda-java-events`, AWS SDK
for Java v2 (`s3`, `dynamodb`), Apache PDFBox 3 (geração do PDF), ZXing `core` (QR Code), React + Vite
(front-end) e AWS SAM CLI (deploy). Cada uma atende a uma necessidade que o JDK e o Spring não cobrem
(D-017 impede o Spring nas funções). Versões conferidas no Maven Central e no npm no dia do uso, nunca
de memória; nenhuma outra biblioteca entra sem aviso e nova entrada aqui.

## D-020 — Runtime `java21`, bytecode 17
**Data:** 24/10/2026 · **Origem:** autor · **Status:** Vigente

As funções rodam no runtime `java21` (Amazon Linux 2023, deprecação em 30/06/2029), compiladas com
`release 17`, o que mantém a D-011. O `java17` gerenciado roda em Amazon Linux 2, cujo fim de vida foi
30/06/2026, e tem deprecação em 30/06/2027. Escolhido o `java21` por ser o runtime mais novo
compatível com o bytecode já decidido. Alternativas descartadas: `java17.al2023`, que roda Java 17 em
AL2023 (deprecação em 2029) e serviria, mas é a trilha para quem não pode atualizar o Java; e
`java25`, também em AL2023, que exigiria rever a D-011. Fonte:
https://docs.aws.amazon.com/lambda/latest/dg/lambda-runtimes.html#runtimes-supported (consultada em
24/10/2026).

## D-021 — Modelo de persistência e idempotência da AV04
**Data:** 24/10/2026 · **Origem:** autor · **Status:** Vigente

O `uploadId` é o id da calibração: a `fn-api` cria `CAL#<id>` em `PROCESSANDO` antes do envio. Cinco
estados (`PROCESSANDO`, `PROCESSADA`, `REJEITADA`, `DUPLICADA`, `EMITIDA`) no lugar dos da AV02. O mesmo
hash em outro envio marca `DUPLICADA` com ponteiro para o original; o mesmo hash no mesmo `id` (evento
repetido do S3) é retomada e não altera nada. A planilha rejeitada por regra de negócio grava a
sentinela `HASH#`, e o reenvio é encerrado sem abrir o Excel; falha técnica não grava. Sem item `ARQ#`
(nome do arquivo é proibido) e com o resultado embutido no `META`. O GSI2 passa a ser `USER#<sub>` +
instante, para a lista do usuário (T12); a fila por estado da AV02 pertence à aprovação, que a D-018
deixou fora. GSI1 não é criado. Detalhe em `docs/av04/modelo-dados.md`. Limitação aceita: contrato da
planilha alterado não libera hash já rejeitado.
