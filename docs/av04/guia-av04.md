# Guia da AV04 — Plataforma Web e deploy em nuvem

Schulz S.A. · Automação da emissão de certificados de calibração · Prazo: **qui 29/10/2026**

Este guia divide a AV04 em tarefas pequenas, uma por sessão do Claude Code (CLI). Cada tarefa traz
o próprio contexto mínimo, então nenhuma sessão precisa da conversa anterior: o estado fica no
repositório (guia, `docs/decisoes.md`, código e commits), não no chat.

---

## 0. Como usar

**Fluxo por tarefa (uma sessão por tarefa)**

1. Abra o Claude Code na raiz do repositório e rode `/tarefa Txx` (ou cole: "Leia o
   `docs/av04/guia-av04.md` e execute a Txx").
2. A sessão lê o `CLAUDE.md` (automático), a seção da tarefa e **só** os arquivos de **Contexto**.
3. Tarefas grandes (T07, T08, T11) começam no *plan mode*: plano curto, você aprova, depois executa.
4. Ao terminar: testes verdes, **Aceite** conferido, nota de conclusão na tarefa, linha no Registro
   de progresso, trecho do relatório em `docs/av04/AV4_fonte.md` (seção indicada em **Relatório**)
   e commit.
5. `/clear` e próxima tarefa.

**Modo de cada tarefa** (troque o ícone se quiser outro modo; o prazo pesa a favor do 🛠)

- 🧑‍🏫 **Instrutivo** — você escreve; o Claude explica, faz no máximo 3 perguntas e revisa o
  `git diff` com no máximo 3 pontos (✅ certo, 🔧 corrigir, 📚 aprender). Reservado para o que
  tem valor de aprendizado ou de defesa na banca.
- 🛠 **Delegado** — o Claude escreve; você revisa o diff antes do commit e pergunta o que não
  entender. Para boilerplate, configuração e trabalho braçal.

**Legenda:** ★ caminho mínimo · ◇ reforço (só se sobrar tempo) · 🔒 segurança/LGPD (não pular)

**Cronograma (uma pessoa, dias úteis só à noite)**

| Dia         | Tarefas         | Marco                                                          |
| ----------- | --------------- | -------------------------------------------------------------- |
| Sáb 24/10   | T00 a T05       | Decisões, conta AWS pronta, projeto multimódulo, persistência  |
| Dom 25/10   | T06 a T09       | **Pipeline funcionando na nuvem via `curl`** (marco crítico)   |
| Seg 26/10   | T10 e T11       | Login e processamento na tela, rodando local contra a nuvem    |
| Ter 27/10   | T12 a T14       | Histórico, verificação pública e SPA publicada no CloudFront   |
| Qua 28/10   | T15 e T16       | Evidências, matriz de rastreabilidade e vídeo no YouTube       |
| Qui 29/10   | T17 e T18       | Relatório em PDF, revisão e envio no AVA                       |

**Se atrasar, corte nesta ordem:** T19 (auditoria) → QR no PDF (fica só a URL impressa) →
T12 (histórico) → layout do PDF. Não se corta: Cognito, pipeline por evento, vídeo, matriz.

---

## 1. O que o roteiro pede (e o que falta nele)

| Item do roteiro                         | O que exige                                                                  | Onde está    |
| --------------------------------------- | ---------------------------------------------------------------------------- | ------------ |
| Objetivo                                | Integrar AV01–AV03 numa Plataforma Web **ou** app mobile; publicar na AWS, estritamente no Free Tier | Todo o guia |
| 1. Relatório Técnico Integrado (PDF)    | Plataforma documentada, **roteiro de deploy na AWS**, **Matriz de Rastreabilidade de Requisitos** | T15, T17 |
| 2. Repositório (GitHub público)         | Código do front-end e **scripts serverless de deploy**                       | T08, T14, T18 |
| 3. Evidência em nuvem                   | **Vídeo no YouTube com narração**, app rodando na nuvem, todos os passos; link no PDF | T16 |
| Cabeçalho obrigatório                   | UC, docente, integrantes (nome completo), link do GitHub                    | T17          |
| Seções 3 e 4 do roteiro                 | **Não constam no PDF** (pula da 2 para a 5) — perguntar (T00)                | T00          |

**Orientação do professor na AV02 (continua valendo):** upload da planilha no storage dispara a
função serverless que lê o Excel, grava no banco e gera o PDF; sem ICP-Brasil, só **autenticação
de usuário** no PDF; retenção de 5 anos com regras de ciclo de vida; custo R$ 0,00.

**Recorte desta entrega (D-018):** passos 1–7 e 10 do fluxo da AV02 (§1.1), com emissão automática
(a D-010 já previa fundir minuta e emissão por configuração). Ficam no roadmap: aprovação do
signatário e RN-12 (passo 8), numeração sequencial e Object Lock (passo 9), TOTP no Cognito,
incerteza/k/veff (R-46 a R-50).

---

## 2. Arquitetura alvo

```
 Navegador (React, CloudFront + S3 privado)
   │ 1. login ──────────────► Cognito (USER_PASSWORD_AUTH, sem segredo de cliente)
   │ 2. POST /uploads (JWT) ─► API Gateway HTTP API ─► fn-api ──► URL pré-assinada (PUT, 5 min, 5 MB)
   │ 3. PUT planilha ───────► S3 entrada  uploads/<sub>/<uploadId>.xlsx
   │                              │ 4. s3:ObjectCreated (assíncrono)
   │                              ▼
   │                         fn-ingestao ── núcleo AV03 (Java puro) ──► DynamoDB calibracoes
   │                              │ 6. destino de sucesso          (HASH# sentinela = RN-20)
   │                              ▼                     falha ──► SQS DLQ
   │                         fn-certificado ── PDFBox ──► S3 certificados  certificados/<id>.pdf
   │ 10. GET /calibracoes/{id}, /pdf (URL pré-assinada), GET /verificacao/{token} (público)
```

**Código (D-017):** Maven multimódulo — `nucleo/` (domínio + ingestão, Java puro, D-012),
`app-local/` (Spring Boot da AV03, segue rodando localmente), `funcoes/` (handlers Lambda e
adaptadores AWS), `web/` (React + Vite), `infra/` (template SAM e scripts de deploy).

---

## Fase 0 — Preparação e decisões

### [x] T00 · Perguntar ao professor o que falta no roteiro ★ 🧑‍🏫

**Contexto.** Seção 1 deste guia. PDF `~/Faculdade/Projeto aplicado III/Entrega AV4 - Plataforma Web.pdf`.

**Faça.** Mande hoje (sáb) uma mensagem curta e fechada. Sugestão:

> Professor, no roteiro da AV04 o documento passa da seção 2 para a 5. As seções 3 e 4 trazem
> requisitos ou critérios de avaliação? Se sim, pode enviá-las? Duas confirmações: (1) seguiremos
> com a Plataforma Web (não mobile), com uma página pública de verificação do certificado por QR
> Code acessível pelo celular; (2) o vídeo pode ser publicado no YouTube como "não listado"?

Não espere a resposta: até ela chegar, as premissas são as da seção 1 e viram `[PREMISSA]` no
relatório.

**Aceite.** Mensagem enviada; data registrada na nota desta tarefa.

> **Concluída em 24/10.** Perguntas enviadas ao professor em 24/10/2026; sem resposta até aqui. Na AV03 o professor já enviou arquivo com numeração de seções errada, confirmado por mensagem, sem conteúdo nas seções faltantes; por isso seguimos sem as seções 3 e 4 e as premissas da seção 1 viram `[PREMISSA]` no relatório (AV4_fonte §12).

---

### [x] T01 · Registrar as decisões D-015 a D-020 ★ 🧑‍🏫

**Contexto.** `docs/decisoes.md` (formato), seções 1 e 2 deste guia, `docs/av02/AV2_Grupo39_fonte.md`
§1.2 e §3.2.

**Conceito.** Como na AV03: decisão escrita antes do código. Cada uma em 5–8 linhas, com data,
origem "autor" e status.

**Faça.** Escreva, no formato existente:

- **D-015 · Plataforma Web, não mobile.** Público principal é o engenheiro no laboratório (upload,
  revisão, PDF). A "validação por QR Code" do roteiro entra como página pública responsiva,
  aberta pela câmera do celular a partir do QR impresso no PDF (RF-16 da AV01).
- **D-016 · Hospedagem da SPA: S3 privado + CloudFront com OAC.** A AV02 não definiu onde fica a
  interface. CloudFront tem plano gratuito (1 milhão de requisições e 100 GB/mês, sem excedente; conferido em 24/10) e dá HTTPS,
  necessário para uma tela de login. Amplify Hosting descartado (cota de 12 meses, programa antigo).
  Confirme a cota na página oficial de preços do CloudFront e cite URL e data.
- **D-017 · Maven multimódulo.** O Lambda não pode carregar o Spring (D-012). Separar `nucleo`,
  `app-local` e `funcoes` é o que prova a D-012 na prática: o mesmo núcleo, duas pontas.
- **D-018 · Recorte da AV04** (seção 1 deste guia) e divergências declaradas em relação à AV02:
  sem TOTP; sem aprovação/RN-12; sem Object Lock; URL pré-assinada **PUT** com `Content-Length`
  assinado no lugar do formulário POST (o SDK Java v2 não gera POST pré-assinado — confirme na T07);
  o arquivo inválido não é movido para `quarentena/`: a rejeição fica registrada no DynamoDB
  com os erros (linha, coluna, regra), visível na tela.
- **D-019 · Bibliotecas externas** (aviso explícito, como na AV03): `aws-lambda-java-core`,
  `aws-lambda-java-events`, AWS SDK for Java v2 (`s3`, `dynamodb`), Apache PDFBox 3, ZXing `core`
  (◇, QR), React + Vite (front-end), AWS SAM CLI (deploy). Verifique as versões no Maven Central e
  no npm no dia; não use versão de memória.
- **D-020 · Runtime `java21`, bytecode 17.** O `java17` gerenciado roda em Amazon Linux 2, cujo
  fim de vida foi 30/06/2026; a AWS recomenda migrar para runtime em AL2023. Compilar com
  `release 17` mantém a D-011 e roda no `java21` (AL2023, suporte até 2029). Fonte: página
  *Lambda runtimes* da AWS (consulte e cite a data).

**Aceite.** Seis decisões no `decisoes.md`, cada uma apontando o requisito ou a decisão anterior
que motiva.

> **Concluída em 24/10.** D-015 a D-020 em `docs/decisoes.md`. Correções ao guia, conferidas nas fontes oficiais: o plano gratuito do CloudFront é 1 milhão de requisições e 100 GB/mês (não 1 TB e 10 milhões); o fim de vida de 30/06/2026 é do Amazon Linux 2, o `java17` vai até 30/06/2027 e existe `java17.al2023`; `java21` e `java25` vão até 30/06/2029. Pendência: confirmar na T10 se o plano gratuito do CloudFront aceita OAC. Escrita delegada pelo Rodrigo por prazo; conteúdo e escolhas (java21, descartes da D-016, enquadramento da D-018) são dele.

**Relatório.** §2 (Decisões da entrega).

---

### [ ] T02 · Preparar a conta AWS e as ferramentas 🔒 ★ 🛠

**Contexto.** D-006 e AV02 §2.2 (plano de monitoramento A-1/A-2).

**Faça.**

- Conta dedicada no plano gratuito (D-006), região `sa-east-1`. Confira o saldo e a validade dos
  créditos em *Billing → Free Tier*.
- Usuário IAM administrativo **com MFA** para o dia a dia; raiz só com MFA e sem chave de acesso.
  Credenciais em `aws configure --profile pa3` (nunca no repositório).
- Budgets: *Zero spend budget* (A-1) e orçamento de US$ 1/mês (A-2), alerta por e-mail.
- Instale e confira: AWS CLI v2, AWS SAM CLI, Node LTS (`node -v`, `npm -v`), JDK 21.
- `.gitignore` (já cobre `.env*`, `node_modules/`, `dist/`): acrescente `.aws-sam/`; decida se o
  `samconfig.toml` entra (só se não tiver nada sensível); o `.claude/` está ignorado inteiro — para
  versionar o comando `/tarefa`, troque a linha `.claude/` por `.claude/*` e `!.claude/commands/`.

**Aceite.** `aws sts get-caller-identity --profile pa3` responde; `sam --version` responde;
print dos dois budgets (sem número de conta visível) em `docs/av04/evidencias/tela/`.

> **Bloqueada em 24/10.** Feito: CLI/SAM/Node/JDK conferidos, perfil `pa3`, `AdministratorAccess`, budgets A-1 e A-2 (prints), `.gitignore`. Pendente: a conta tem SCP que só libera `us-east-2`, o MFA não pôde ser configurado e a conta foi suspensa; a segunda conta não obteve plano gratuito. Aguardando o AWS Support (e o professor, sobre o prazo). Não reabrir a região (D-006) antes da resposta.

**Relatório.** §6 (Roteiro de deploy — pré-requisitos) e §8 (Custo).

---

## Fase 1 — Back-end preparado para a nuvem

### [x] T03 · Converter para Maven multimódulo ★ 🛠

**Contexto.** `pom.xml`, `README.md` (Estrutura), D-012, D-017.

**Faça.**

- POM pai (`packaging pom`, mantendo `spring-boot-starter-parent` como pai, para herdar a gestão de
  versões) com os módulos `nucleo`, `app-local`, `funcoes`.
- `nucleo/`: `dominio/` e `ingestao/` e os testes deles; só depende do POI. As planilhas de teste
  vão para `nucleo/src/test/resources/planilhas/`.
- `app-local/`: `aplicacao/`, `web/`, `static/` e os testes Spring. Se os testes da web leem as
  planilhas, use o *test-jar* do `nucleo` ou caminho relativo — decida e explique no commit.
- Leve o cálculo de SHA-256 em streaming (`Medidor`, hoje no `ProcessamentoService`) para o
  `nucleo`: a `fn-ingestao` vai precisar dele.
- `git mv` (não copiar e apagar), para manter o histórico dos arquivos.
- Atualize o `.gitignore` (caminhos de `planilhas/` e `local/` mudaram) e o README.

**Aceite.** `./mvnw test` na raiz: **71 testes, 0 falhas, 1 pulado** (mesmo número da AV03);
`./mvnw -pl app-local spring-boot:run` abre a tela da AV03 e processa `ok-reprovado.xlsx`;
`grep -r "springframework" nucleo/` vazio.

> **Concluída em 24/10.** Módulos `nucleo`, `app-local` e `funcoes` (este ainda sem código, com `nucleo` como dependência). `git mv` em tudo; `./mvnw test`: 71 testes (56 + 15), 0 falhas, 1 pulado num clone limpo (na máquina do autor o `strict-real.xlsx` local existe e o teste roda). Os testes do `app-local` leem as planilhas pelo *test-jar* do `nucleo` (uma única cópia). `Medidor` foi para `nucleo/ingestao` sem log (o `ProcessamentoService` loga). `spring-boot-maven-plugin` com `skip` no pai e `skip=false` no `app-local`, para `-pl app-local -am spring-boot:run` funcionar.

**Relatório.** §3 (Arquitetura implementada): estrutura de módulos e prova da D-012.

---

### [x] T04 · Modelo de persistência e idempotência (RN-20) ★ 🧑‍🏫 🔒

> **Concluída em 24/10.** Modo mudou de instrutivo para delegado a pedido do Rodrigo, que decidiu as três perguntas (mesmo id; `DUPLICADA` com ponteiro; rejeitada grava a sentinela e o reenvio é curto-circuitado pelo hash). Porta `RepositorioCalibracoes` em `nucleo/persistencia`, adaptador em memória e 11 testes; `./mvnw test`: 82 testes (67 + 15), 0 falhas. `modelo-dados.md` e D-021. Pendente de validação na T06: condição do `TransactWriteItems` no DynamoDB real.

**Contexto.** AV02 §3.2 (tabela única `PK`/`SK`, sentinelas `HASH#`/`TOKEN#`, GSI2), D-007, D-014,
nota da T17 no `docs/av03/guia-av03.md`.

**Conceito.** No DynamoDB não há `UNIQUE`: a unicidade é um item-sentinela gravado **na mesma
transação** (`TransactWriteItems`) que a calibração, com `attribute_not_exists(PK)`. Se o hash já
existe, a transação inteira falha e nada fica pela metade. Checar antes com `GetItem` não basta:
dois envios simultâneos passariam os dois (corrida).

**Perguntas a responder antes de codar:**

1. O `uploadId` (gerado pela `fn-api` antes do envio) e o id da calibração são o mesmo
   identificador? O que a tela consulta enquanto o arquivo ainda está sendo processado?
2. Reenvio do mesmo arquivo: devolver a calibração original, criar um registro `DUPLICADO`
   apontando para ela, ou recusar? O que o usuário vê?
3. Planilha rejeitada: grava o hash como sentinela? (Se gravar, a versão corrigida é outro arquivo
   e outro hash; a mesma planilha errada reenviada cairia em quê?)

**Faça.**

- Porta `RepositorioCalibracoes` no `nucleo` (pacote novo, Java puro), com registro de calibração
  processada, rejeitada (com a lista de `ErroValidacao`) e emitida.
- Desenho dos itens (PK/SK, atributos, estados `PROCESSANDO`/`REJEITADA`/`PROCESSADA`/`EMITIDA`,
  quem grava cada um) numa tabela em `docs/av04/modelo-dados.md`, comparada com a AV02.
- Adaptador em memória para teste (no `nucleo/src/test`) e testes: mesmo hash duas vezes; hash
  diferente; rejeitada sem dado de negócio.
- 🔒 Nunca gravar nome do arquivo nem conteúdo bruto; usuário só pelo `sub` do Cognito (opaco);
  o nome de exibição vai só para o PDF (AV02 §4.3, RN-35).

**Aceite.** Testes verdes; `modelo-dados.md` com as respostas às 3 perguntas.

**Relatório.** §4 (Persistência e idempotência).

---

### [x] T05 · Geração do certificado em PDF ★ 🛠 (layout) / 🧑‍🏫 (conteúdo)

**Contexto.** Certificado real `Cal_1_5896_1_0 (1).pdf` (fora do Git, **só para olhar a
estrutura**), `ResultadoCalibracao`, D-003, PV-14.

**Faça.**

- Classe `GeradorCertificado` no `funcoes` com Apache PDFBox 3 (D-019), recebendo os dados
  calculados + metadados e devolvendo `byte[]`.
- Conteúdo mínimo: identificador, data/hora (America/Sao_Paulo), SHA-256 da planilha de origem,
  **"Gerado por: <usuário autenticado>"** (orientação do professor), tabela de pontos com leituras,
  referência, média, erro, LEA e status (enums literais), parecer em destaque, token de verificação
  e URL `https://<cloudfront>/verificar/<token>`.
- ◇ QR Code da URL com ZXing `core` (desenhar a matriz como retângulos no PDFBox, sem `javase`).
- Rodapé: "Documento gerado por PoC acadêmica (UniSENAI, Projeto Aplicado III) — sem validade
  metrológica". 🔒 Nenhum dado real da Schulz no teste.
- Layout simplificado, não o da Schulz: declarar como divergência de PV-14.

**Aceite.** Teste gera o PDF de `ok-reprovado.xlsx` e confere, com `PDFTextStripper`, o parecer,
o hash e um enum; PDF de exemplo salvo em `docs/av04/evidencias/` e aberto por você.

**Relatório.** §5 (Certificado gerado), com print.

> **Concluída em 24/10.** `GeradorCertificado` + `DadosCertificado` em `funcoes`
> (PDFBox 3.0.8, ZXing `core` 3.5.4, QR desenhado como retângulos). 85 testes verdes (3 novos).
> PDF de exemplo: `docs/av04/evidencias/certificado-ok-reprovado.pdf` (planilha sintética).
> Divergência de PV-14: layout simplificado, sem o formato da Schulz. PDF aberto e conferido pelo Rodrigo; print em `evidencias/tela/relatorio-pdf.png`.

---

## Fase 2 — Funções e infraestrutura

### [ ] T06 · `fn-ingestao` ★ 🧑‍🏫

**Contexto.** `ProcessamentoService` (AV03), porta da T04, `LeitorPlanilha`, AV02 §3.1 (E3–E5).

**Faça.**

- Handler `RequestHandler<S3Event, ...>`: extrai `sub` e `uploadId` da chave, abre o objeto em
  *stream*, passa pelo `Medidor` (hash) e pelo núcleo, grava pela porta.
- Inválida → grava `REJEITADA` com os erros e **termina com sucesso** (quarentena é resultado de
  negócio, não falha — AV02 §3.1.3); não aciona o certificado. Erro inesperado → lança exceção
  (vai para a DLQ). Duplicada → conforme a resposta 2 da T04.
- Retorno no sucesso: `{ "calibracaoId": ... }` (é o que a `fn-certificado` recebe no destino).
- Log: só `uploadId`, `sha256`, `bytes`, resultado. 🔒 Nada de nome de arquivo.
- Adaptador DynamoDB real da porta (SDK v2, `TransactWriteItems`) no `funcoes`.
- Teste unitário com S3 e repositório falsos (sem AWS).

**Aceite.** Testes verdes para: válida, inválida (R10), duplicada, exceção inesperada.

**Relatório.** §3.

---

### [ ] T07 · `fn-certificado` e `fn-api` ★ 🛠 (*plan mode*)

**Contexto.** T04–T06, AV02 §3.1 (E1, E2, E6, E10) e §4.1 (tabela de papéis).

**Faça.**

- `fn-certificado`: recebe o envelope do destino do Lambda (`responsePayload.calibracaoId`), carrega,
  gera o PDF (T05), grava `certificados/<id>.pdf` (SSE-S3), grava o item `TOKEN#<token>` (32
  caracteres aleatórios, `SecureRandom`) e muda o estado para `EMITIDA` com o hash do PDF.
- `fn-api` (um handler, roteamento por `routeKey` do evento HTTP API v2):
  - `POST /uploads` `{tamanho}` → recusa > 5 MB; devolve `uploadId` e URL **PUT** pré-assinada
    (5 min, `Content-Type` e `Content-Length` assinados) para `uploads/<sub>/<uploadId>.xlsx`.
  - `GET /calibracoes/{id}` → estado + resultado ou erros; só do próprio `sub` (senão 404, não 403,
    para não confirmar existência).
  - `GET /calibracoes/{id}/pdf` → URL GET pré-assinada de 5 min.
  - `GET /calibracoes` → ◇ (T12) últimas 20 do usuário.
  - `GET /verificacao/{token}` → **público**: identificador, data, parecer, hash do PDF. Sem nome.
  - Erros em `application/problem+json` (mesmo padrão da AV03); id malformado → 400.
- Testes unitários dos dois handlers com falsos.

**Aceite.** Testes verdes; nenhuma rota devolve dado de outro `sub`; `./mvnw -pl funcoes -am
package` gera um único JAR sombreado (`maven-shade-plugin`) sem classes do Spring.

**Relatório.** §3 e §7 (API).

---

### [ ] T08 · Template SAM e deploy do back-end ★ 🛠 (*plan mode*) / 🧑‍🏫 (IAM) 🔒

**Contexto.** AV02 §1.2, §2 (cotas) e §4 (IAM, criptografia), D-004, D-007, D-008, D-009, D-016,
D-020.

**Faça** `infra/template.yaml` (+ `infra/deploy.sh`):

- **S3:** `entrada` e `certificados` com bloqueio de acesso público, `DenyInsecureTransport`, SSE-S3,
  versionamento, **ciclo de vida** (transição para classe de arquivamento em 365 dias e expiração
  em 1 825 dias — D-004) e CORS do `entrada` só para a origem do CloudFront (e `localhost` em dev).
  `web` privado, servido pelo CloudFront (OAC), com resposta 403/404 → `/index.html` (rotas da SPA).
- **DynamoDB:** `calibracoes` (+GSI2) e ◇ `auditoria`, modo **provisionado** dentro das 25 RCU/WCU.
- **Lambda:** três funções `java21`, `CodeUri` apontando para o JAR sombreado (o `sam build` não
  resolve o módulo `nucleo` sozinho), timeout 30 s, memória 1 024 MB, logs com retenção de 30 dias;
  `fn-ingestao` por evento S3 em `uploads/`, destino de sucesso → `fn-certificado`, falha → SQS DLQ.
- **Cognito:** user pool sem autocadastro, cliente público com `ALLOW_USER_PASSWORD_AUTH` e
  `ALLOW_REFRESH_TOKEN_AUTH`, token de acesso de 60 min (RNF-05).
- **HTTP API:** autorizador JWT do Cognito em todas as rotas exceto `GET /verificacao/{token}`,
  CORS, *throttling* (AV02: 10 req/s).
- 🧑‍🏫 **IAM:** um papel por função, só as ações da AV02 §4.1 (ex.: `fn-ingestao` com `s3:GetObject`
  só em `uploads/*`). Você escreve as políticas; o Claude revisa.
- **Saídas** do stack: URL da API, URL do CloudFront, ids do user pool e do cliente.

Deploy: `sam deploy --guided --profile pa3 --region sa-east-1` na primeira vez.

**Aceite.** Stack `CREATE_COMPLETE`; nenhum recurso fora da lista acima (sem NAT, sem KMS próprio,
sem DynamoDB sob demanda); `aws s3api get-bucket-lifecycle-configuration` mostra a regra.

**Relatório.** §6 (Roteiro de deploy) e §4.

---

### [ ] T09 · Teste de ponta a ponta na nuvem, sem tela ★ 🛠 🔒

**Contexto.** Saídas do stack (T08), `docs/av03/evidencias.md` (modelo de CT e laço `curl`).

**Faça** `infra/smoke.sh` (variáveis vindas das saídas do stack, nada fixo no arquivo):

1. Criar usuário de teste: `admin-create-user` + `admin-set-user-password --permanent` (evita o
   desafio `NEW_PASSWORD_REQUIRED` na primeira entrada).
2. Token via `aws cognito-idp initiate-auth --auth-flow USER_PASSWORD_AUTH`.
3. `POST /uploads` → `PUT` do `ok-reprovado.xlsx` → *polling* do `GET /calibracoes/{id}` →
   `GET .../pdf` → baixa o PDF → `GET /verificacao/{token}` sem token.
4. Repetir com o mesmo arquivo (idempotência), com `inv-multiplos-erros.xlsx` (rejeição) e sem
   token (401 na borda).

🔒 As respostas salvas em `docs/av04/evidencias/respostas/` não podem conter JWT, URL pré-assinada
(tem credencial temporária) nem número da conta: filtre com `jq` antes de gravar.

**Aceite.** Os cinco cenários com o resultado esperado; log da `fn-ingestao` no CloudWatch só com
hash e tamanho. **Marco de domingo: se isso passa, a AV04 está garantida.**

**Relatório.** §9 (Evidências).

---

## Fase 3 — Plataforma web

### [ ] T10 · Projeto React e login ★ 🛠 / 🧑‍🏫 (fluxo de token) 🔒

**Contexto.** `app-local/.../static/` (tela da AV03: reaproveitar comportamento, cores e rótulos),
saídas do stack.

**Faça.**

- `web/` com Vite + React (D-019). Sem biblioteca de roteamento nem de estado: duas ou três telas
  resolvem com estado simples e `location.pathname` para a rota pública `/verificar/<token>`.
- Configuração por `import.meta.env` (`VITE_API_URL`, `VITE_COGNITO_CLIENT_ID`, região), gerada a
  partir das saídas do stack por script; `.env` fora do Git.
- Login com `fetch` direto ao Cognito (`InitiateAuth`, `USER_PASSWORD_AUTH`), sem SDK. A senha vai
  do navegador ao Cognito por HTTPS, nunca à aplicação (AV02 §4.3).
- 🔒 Tokens **só em memória** (estado React), nunca em `localStorage`; recarregar a página pede
  login de novo — declarar. Mensagem de erro genérica para usuário/senha inválidos.

**Aceite.** `npm run dev` faz login no Cognito real e mostra o usuário; senha errada mostra erro
sem detalhe técnico.

**Relatório.** §5 (Plataforma) e §7 (Segurança).

---

### [ ] T11 · Tela de processamento ★ 🛠 (*plan mode*)

**Contexto.** `app-local/.../static/app.js` e `estilo.css` (AV03), achados 11 e 12 da AV03
(corrida na troca de arquivo).

**Faça.**

- Escolher arquivo → `POST /uploads` → `PUT` na URL → *polling* com limite (ex.: a cada 1,5 s,
  até 40 s; depois, mensagem "ainda processando, consulte no histórico").
- Estados visíveis: enviando, processando, `REJEITADA` (lista de erros com linha, coluna, regra),
  resultado (tabela da AV03: enums literais, cor + rótulo, 5 casas com vírgula), botão **Baixar
  certificado**.
- Mesmas proteções da AV03: limpar ao trocar de arquivo, travar durante o envio, só texto (o React
  já escapa; não usar `dangerouslySetInnerHTML`).
- RNF-15: contraste AA, alvos de toque grandes, funciona em tablet (DevTools, 768 px).

**Aceite.** Fluxo completo local contra a nuvem com `ok-aprovado`, `ok-reprovado` e
`inv-multiplos-erros`; prints.

**Relatório.** §5, com prints.

---

### [ ] T12 · Histórico de calibrações ◇ 🛠

**Contexto.** GSI da T04/T08, rota `GET /calibracoes` (T07).

**Faça.** Lista das últimas 20 do usuário (data, estado, parecer), clique abre o resultado. É o
que faz a plataforma ser "de gestão", e não só a tela da AV03.

**Aceite.** Lista mostra os envios da T09 e T11.

---

### [ ] T13 · Página pública de verificação (QR) ★ 🛠

**Contexto.** Rota `GET /verificacao/{token}` (T07), D-015.

**Faça.** `/verificar/<token>` sem login: identificador, data, parecer, hash do PDF e a instrução
"compare com o hash impresso no documento". Layout para celular. Token inválido → "certificado não
encontrado" (sem distinguir de token malformado).

**Aceite.** Ler o QR do PDF (ou abrir a URL impressa) com o celular abre a página certa.

**Relatório.** §5.

---

### [ ] T14 · Publicar a SPA ★ 🛠

**Contexto.** T08 (bucket `web`, CloudFront), T10.

**Faça** `infra/deploy-web.sh`: gerar `.env` das saídas do stack → `npm ci && npm run build` →
`aws s3 sync web/dist s3://<bucket-web> --delete` → invalidação `/*` no CloudFront. Ajustar CORS da
API e do bucket `entrada` para a origem do CloudFront.

**Aceite.** Fluxo completo pela URL do CloudFront, em aba anônima, e a verificação no celular por
4G (fora da rede de casa).

**Relatório.** §6.

---

## Fase 4 — Evidências, vídeo e relatório

### [ ] T15 · Evidências e Matriz de Rastreabilidade ★ 🛠 / 🧑‍🏫 (matriz)

**Contexto.** `docs/av03/evidencias.md` (modelo), AV01 (RF/RNF/RN/UC — só os códigos, no
`AV2_Grupo39_fonte.md`), AV02 §5 (matriz de decisões).

**Faça.**

- `docs/av04/evidencias.md`: CT-401 em diante (nuvem: os cinco cenários da T09, tela, verificação,
  401 sem token, 404 de outro usuário, URL pré-assinada expirada, ciclo de vida, logs sem dado
  pessoal, custo). Uma linha de base: commit + data do deploy.
- **Matriz de Rastreabilidade de Requisitos** (`docs/av04/matriz-rastreabilidade.md`): requisito
  (AV01) → decisão/passo da arquitetura (AV02) → componente/classe → teste automatizado ou CT →
  evidência. Inclua os requisitos **não** atendidos, com o motivo (roadmap).
- Custo: print de *Billing/Free Tier* e do Cost Explorer com US$ 0,00 (ou só consumo de créditos),
  e das métricas de uso (invocações do Lambda, RCU/WCU) contra as cotas.

**Aceite.** Todo item do roteiro tem CT e evidência; todo RF do recorte aparece na matriz.

**Relatório.** §9 e §10.

---

### [ ] T16 · Vídeo no YouTube com narração ★ 🧑‍🏫 🔒

**Contexto.** Seção 2 deste guia, T15.

**Faça.**

- Roteiro de 5–8 min em `docs/av04/roteiro-video.md`: problema (13 dias, 30 s) → arquitetura
  (diagrama) → login → upload aprovado e reprovado → PDF → QR no celular → planilha inválida →
  reenvio (idempotência) → console AWS: Lambda, DynamoDB, S3 com ciclo de vida, logs, custo zero.
- Gravar (OBS ou similar), uma tomada por bloco; publicar (não listado, se o professor aceitar).
- 🔒 Antes de gravar: nada de número de conta, e-mail pessoal, token, URL pré-assinada, chave ou
  dado real da Schulz na tela. Assista uma vez só procurando isso.

**Aceite.** Link funcionando em aba anônima.

**Relatório.** Link no cabeçalho e na §9.

---

### [ ] T17 · Relatório Técnico Integrado em PDF ★ 🧑‍🏫 (texto) / 🛠 (formatação)

**Contexto.** `docs/av04/AV4_fonte.md` (montado ao longo das tarefas), pipeline da AV03 (pandoc,
DOCX da AV2 como `--reference-doc`, `ferramentas/formatar_docx.py`, LibreOffice).

**Estrutura:** cabeçalho obrigatório (UC, docente, integrantes com nome completo, GitHub, YouTube)
· 1 Introdução e vínculo com AV01–AV03 · 2 Decisões · 3 Arquitetura implementada (diagrama
atualizado, divergências da AV02) · 4 Persistência e idempotência · 5 Plataforma Web (telas) ·
6 **Roteiro de deploy na AWS** (reprodutível do zero) · 7 Segurança e LGPD · 8 Custo e Free Tier ·
9 Evidências · 10 **Matriz de Rastreabilidade** · 11 Limitações e roadmap · 12 Premissas
(`[PREMISSA]`, `[VALIDAR]`, seções 3–4 do roteiro) · Referências.

**Aceite.** PDF aberto e lido por você; links clicáveis; nenhum dado pessoal de terceiros.

---

### [ ] T18 · README, revisão final e envio ★ 🛠

- README: o que mudou na AV04, URL da plataforma, como fazer o deploy (resumo da §6), estrutura dos
  módulos, limitações.
- Clone limpo: `./mvnw test` verde e o `infra/deploy.sh` documentado.
- Checklist: roteiro inteiro coberto · enums literais no código, API e tela · nada real da Schulz no
  Git · repositório público · vídeo acessível · cabeçalho completo · PDF exportado com folga.
- 🔒 **Não derrubar o stack** antes da nota: o professor pode abrir a URL. Depois, `sam delete`.

---

### [ ] T19 · Trilha de auditoria ◇ 🛠 🔒

Tabela `auditoria` só de inserção (AV02 §3.2, RNF-04, RN-24): eventos `UPLOAD_AUTORIZADO`,
`PROCESSADA`, `REJEITADA`, `EMITIDA`, `PDF_BAIXADO`, com `sub`, instante e id. Papéis das funções
sem `UpdateItem`/`DeleteItem` nela.

---

## Registro de progresso

| Tarefa | Modo | Status   | Observação |
| ------ | ---- | -------- | ---------- |
| T00    | 🧑‍🏫   | Concluída | Perguntas enviadas em 24/10; sem resposta, seguimos com premissas |
| T01    | 🧑‍🏫   | Concluída | D-015 a D-020; OAC no plano gratuito a confirmar na T10 |
| T02    | 🛠   | Bloqueada | Conta AWS suspensa; SCP só `us-east-2`; sem MFA; caso no suporte (24/10) |
| T03    | 🛠   | Concluída | Multimódulo; 71 testes verdes; `Medidor` no núcleo |
| T04    | 🧑‍🏫→🛠 | Concluída | Porta + adaptador em memória; 82 testes verdes; D-021; falta provar no DynamoDB (T06) |
| T05    | 🛠   | Concluída | PDF com QR; 85 testes verdes; divergência de PV-14 (layout simplificado) |
| T06    | 🧑‍🏫   | Pendente |            |
| T07    | 🛠   | Pendente |            |
| T08    | 🛠   | Pendente |            |
| T09    | 🛠   | Pendente |            |
| T10    | 🛠   | Pendente |            |
| T11    | 🛠   | Pendente |            |
| T12    | 🛠   | Pendente |            |
| T13    | 🛠   | Pendente |            |
| T14    | 🛠   | Pendente |            |
| T15    | 🛠   | Pendente |            |
| T16    | 🧑‍🏫   | Pendente |            |
| T17    | 🧑‍🏫   | Pendente |            |
| T18    | 🛠   | Pendente |            |
| T19    | 🛠   | Pendente |            |
