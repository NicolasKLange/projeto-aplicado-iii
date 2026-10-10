# Guia da AV03 — MVP parte 1 (aplicação transacional)

Grupo 39 · Schulz S.A. · Automação da emissão de certificados de calibração

Este guia quebra a AV03 em tarefas pequenas, feitas uma de cada vez. Ele não traz código
pronto: cada tarefa explica o conceito, diz o que fazer e o que trazer para revisão.

---

## 0. Como usar

**Fluxo por tarefa**

1. Leia a tarefa inteira antes de começar.
2. Faça. Se travar por mais de 20 minutos, traga a dúvida em vez de insistir.
3. Mande no chat: `Txx feito` + o que a seção **Traga** pede.
4. Eu reviso com no máximo 3 pontos (o que está certo, o que corrigir, o que aprender).
5. Marque o checkbox e passe para a próxima.

**Legenda**

- ★ = caminho mínimo (sem isso a entrega fica incompleta)
- ◇ = reforço (sobe a nota ou o aprendizado; faça se o prazo permitir)
- 🔒 = ponto de segurança/LGPD (contexto de medical device: não pular)

**Cronograma até 15/10 (quinta-feira).** Só o caminho ★ cabe. O fim de semana concentra
as decisões e o núcleo, porque os dias úteis rendem menos.

| Dia | Tarefas | Marco |
|---|---|---|
| Sáb 10/10 | T01 a T05 | Decisões registradas e contrato da planilha fechado |
| Dom 11/10 | T06 a T10 | Núcleo de cálculo com testes verdes |
| Seg 12/10 | T12 e T13 | Planilha lida e validada |
| Ter 13/10 | T14 a T16 e T18 | Fluxo completo na tela, evidências coletadas |
| Qua 14/10 | T19 e T20 | Repositório público e relatório redigido |
| Qui 15/10 | T21 | Revisão e envio no AVA |

T11 e T17 (◇) só entram se algum dia terminar adiantado.

**Divisão no grupo (sugestão).** As fases 1 e 2 podem andar em paralelo: uma pessoa no
contrato e nas planilhas de teste, outra no núcleo de cálculo. A fase 3 depende das duas.

---

## 1. O que o roteiro pede (e o que falta nele)

| Item do roteiro | O que exige | Onde está neste guia |
|---|---|---|
| Item 1.1 — Ingestão | Ler o Excel enviado pelo usuário com leituras e valores do padrão | Fases 1 e 3 |
| Item 1.2 — Regras | Média por ponto · erro sistemático (média − padrão) · \|erro\| vs LEA → `DENTRO_DA_TOLERANCIA` / `FORA_DA_TOLERANCIA` · parecer `APROVADO` / `REPROVADO` | Fase 2 |
| Item 2 | Não consta no PDF. Tratado como erro de numeração (pergunta enviada ao professor em 10/10; se a resposta mudar algo, revisar) | — |
| Objetivo da entrega (seção 1) | "Apresentar os resultados calculados e o parecer final numa interface limpa e intuitiva" | T16 |
| Item 3 — Evidências | Relatório PDF no AVA com evidências + link do GitHub **público** com todo o código | Fase 5 |
| Critério de avaliação | Funcionamento prático, coerência da arquitetura transacional, evidências de teste | Todas |

**Nomes exatos.** Os enums do roteiro (`DENTRO_DA_TOLERANCIA`, `FORA_DA_TOLERANCIA`,
`APROVADO`, `REPROVADO`) devem aparecer **literalmente** no código e na tela. É o primeiro
lugar onde o professor vai olhar.

**Diferença em relação à AV01.** O roteiro simplifica o cálculo: não pede desvio padrão,
incerteza, k nem veff (R-46 a R-50 da AV01). Implemente exatamente o que o roteiro pede.
O resto vai para "limitações e próximos passos" no relatório, com os códigos da AV01.

---

## Fase 0 — Preparação e decisões

### [x] T00 · Confirmar prazo e o Item 2 com o professor ★

> **Concluída em 10/10.** Prazo confirmado: 15/10/2026. Pergunta sobre o Item 2 enviada;
> até a resposta, o grupo segue só com os itens existentes.

**Objetivo.** Não construir no escuro. O roteiro não tem data nem Item 2.

**Faça.**
- Confirme a data da AV03 no AVA (o guia antigo dizia 15/10/2026).
- Mande ao professor uma pergunta curta e fechada. Sugestão de texto:

  > Professor, no roteiro da AV03 (Parte 1) o documento passa do Item 1 para o Item 3.
  > O Item 2 seria a interface web que apresenta os resultados? Se houver requisitos
  > específicos para ele, pode nos enviar? Outra dúvida: o repositório precisa ser público;
  > podemos usar planilhas sintéticas no lugar dos arquivos reais da Schulz, que contêm
  > dados de cliente e de técnico?

- Não espere a resposta para seguir: a regra de sobrevivência do grupo continua valendo.
  Se não responder, a premissa é "Item 2 = interface web" e vira `[PREMISSA]` no relatório.

**Traga.** A data confirmada e, quando houver, a resposta.

---

### [ ] T01 · Escolher a stack e registrar como D-011 ★

**Conceito.** Decisão de arquitetura (ADR) é escrita *antes* do código porque explica ao
avaliador por que o projeto é como é. O `docs/decisoes.md` já segue esse formato.

**Opções.**

| Opção | A favor | Contra |
|---|---|---|
| **A. Java 17 + Spring Boot 3 + Maven** | Stack que você usa no trabalho; o guia antigo já apontava essa escolha; JUnit 5 vem junto | Mais cerimônia para subir |
| B. Python + FastAPI | Menos código; openpyxl é simples | Não exercita o que você quer aprender; openpyxl também não lê Strict |
| C. Node + Express | Combina com React na AV04 | Cálculo decimal exige biblioteca extra |

**Interface (Item 2).** Para a parte 1, uma página estática (HTML + JS puro) servida pelo
próprio Spring Boot resolve sem nenhuma dependência nova. React fica para a AV04.

**⚠️ Bibliotecas externas.** Para ler `.xlsx` em Java, o caminho realista é o
**Apache POI** (`poi-ooxml`). É dependência externa. Ler o XML na mão é possível
(o `.xlsx` é um zip de XMLs), mas não vale o tempo nesta entrega.

**Faça.**
- Escolha a opção (discuta com o grupo: todos conseguem rodar?).
- Escreva a D-011 no `docs/decisoes.md` com o formato existente: data, origem, status e
  5–8 linhas de justificativa.

**Traga.** O texto da D-011.

---

### [ ] T02 · Amarrar o MVP à arquitetura da AV02 (D-012) ★

**Conceito.** A AV02 descreveu um pipeline serverless (S3 → `fn-ingestao` → DynamoDB →
`fn-certificado`). A AV03 pede uma aplicação web transacional. Isso não é contradição se o
desenho for **núcleo independente + adaptadores** (arquitetura hexagonal, versão leve):

```
            ┌──────────── adaptadores de entrada ────────────┐
 navegador → Controller REST (AV03)      evento S3 → Handler Lambda (futuro)
            └───────────────────┬────────────────────────────┘
                                ▼
                 Núcleo: LeitorPlanilha + CalculadoraMetrologica
                 (Java puro, sem Spring, sem AWS)
```

O núcleo de cálculo da AV03 é o mesmo código que a `fn-ingestao` executaria. Esse é o
argumento de "coerência arquitetural" do relatório.

**Pergunte-se antes de escrever:**
- Por que o núcleo não pode importar nada do Spring?
- O que muda, e o que não muda, quando o mesmo núcleo rodar dentro de uma Lambda?

**Faça.** Escreva a D-012 (5–8 linhas) com sua resposta.

**Traga.** O texto da D-012 e as respostas às duas perguntas.

---

### [ ] T03 · Preparar o repositório para ficar público 🔒 ★

**Conceito.** Tornar público um repositório expõe **todo o histórico**, não só a versão
atual. Um arquivo removido num commit continua acessível nos commits antigos.

**Faça.**
- Liste o que já foi versionado: `git log --all --name-only --format="" | sort -u`.
- Verifique se `docs/av01/` e `docs/av02/` (DOCX/PDF) citam nome ou matrícula de técnico,
  nome de cliente/fornecedor ou números de série reais. Decida: manter, anonimizar ou tirar
  do repositório.
- Atenção: a planilha real carrega metadado pessoal (caminho `C:\Users\<nome do usuário>\...`
  dentro do `workbook.xml`). Nenhum arquivo da Schulz entra no Git, nem em `src/test`.
- Confirme que o `.gitignore` bloqueia `*.xlsx`, `*.xlsm` e `*.pdf` fora das pastas
  permitidas (ex.: permitir só `src/test/resources/planilhas/`).
- Se algo sensível já está no histórico, traga antes de tornar público: removemos juntos.

**Traga.** A lista de arquivos versionados e a sua decisão para cada documento sensível.

---

## Fase 1 — Contrato de entrada

### [ ] T04 · Mapear o modelo do roteiro para os dados reais ★

**Conceito.** O roteiro usa vocabulário genérico de calibração de instrumento. A Schulz
calibra dispositivos de controle medidos na MMC. Antes de codificar, é preciso dizer o que
cada termo significa no caso real.

**Faça.** Abra o certificado 00091/2026 e as planilhas (localmente) e preencha:

| Termo do roteiro | O que é na Schulz | Onde está (arquivo/aba/célula) |
|---|---|---|
| Ponto de ensaio | ? (dica: característica) | |
| Leituras | ? (quantas por ponto?) | |
| Valor padrão de referência | ? (nominal? valor do padrão?) | |
| LEA | ? (tolerância?) | |

**Perguntas a responder:**
- A tolerância da Schulz é sempre simétrica (± 0,05) ou existe assimétrica (+0,05 / −0,02)?
  O LEA do roteiro é um único valor. Se houver assimetria, qual é a decisão do grupo?
- Fronteira: um ponto com |erro| **exatamente igual** ao LEA está dentro ou fora?
  (O padrão usual é dentro: `|E| ≤ LEA`. Escolha e justifique.)
- Isso tem relação com o bug de sinal AN-17 / RN-05 da AV01?

**Traga.** A tabela preenchida e as três respostas.

---

### [ ] T05 · Escrever o contrato da planilha ★

**Conceito.** A AV02 deixou explícito: *"O layout exato de abas e colunas é fixado como
contrato na Entrega 03 [PREMISSA]"*. Esta tarefa fecha essa premissa. Contrato bom é aquele
que permite a outra pessoa criar um arquivo válido sem perguntar nada.

**Faça.** Crie `docs/av03/contrato-planilha.md` definindo:
- Nome da aba (ex.: `Medicoes`) e se a ordem das abas importa.
- Colunas: nome do cabeçalho, tipo, unidade, obrigatoriedade, exemplo.
  Mínimo: identificador do ponto, valor de referência, LEA, leitura 1 … leitura N.
- Número de leituras: fixo ou variável por ponto? Mínimo aceito?
- Separador decimal: como tratar célula de texto "10,03" em vez de número?
- **Formato do arquivo: `.xlsx` Transitional** (o "Pasta de Trabalho do Excel" padrão).
  A planilha real da Schulz está em **OOXML Strict**, e o suporte das bibliotecas a esse
  formato é limitado. Como a planilha sanitizada é do grupo (Tema 01), o contrato pode
  exigir o formato padrão. Registre isso como regra e como risco.
- Regras de rejeição (cada uma vira um teste depois).

**Traga.** O contrato.

---

### [ ] T06 · Criar as planilhas sintéticas de teste 🔒 ★

**Conceito.** Dados sintéticos resolvem dois problemas de uma vez: liberam o repositório
público (LGPD, RA-11) e permitem desenhar casos que talvez não existam nos dados reais,
como o ponto exatamente no limite.

**Faça.** Em `src/test/resources/planilhas/`, crie no Excel ou no LibreOffice (salvando
como `.xlsx` padrão), com identificadores fictícios:

| Arquivo | Cenário | Resultado esperado |
|---|---|---|
| `ok-aprovado.xlsx` | Todos os pontos dentro | `APROVADO` |
| `ok-reprovado.xlsx` | Um ponto fora | `REPROVADO` |
| `ok-limite.xlsx` | \|E\| = LEA exatamente | Conforme decisão da T04 |
| `ok-erro-negativo.xlsx` | Média abaixo da referência, ainda dentro | `APROVADO` (testa o `abs`) |
| `inv-sem-coluna.xlsx` | Falta a coluna LEA | Rejeitado, mensagem cita a coluna |
| `inv-texto.xlsx` | "abc" numa leitura | Rejeitado, mensagem cita linha e coluna |
| `inv-vazia.xlsx` | Só o cabeçalho | Rejeitado (nenhum ponto) |
| `inv-strict.xlsx` | Mesmo conteúdo de um caso ok, salvo como Strict | Rejeitado com mensagem clara |

Para cada arquivo `ok-*`, calcule à mão (numa folha ou calculadora) a média, o erro e o
status de cada ponto. Esses números são o **oráculo** dos testes da fase 2.

**Traga.** A tabela de oráculo de `ok-aprovado` e `ok-reprovado` (ponto, leituras,
referência, LEA, média, erro, status).

---

## Fase 2 — Núcleo de cálculo (Java puro, com TDD)

### [ ] T07 · Esqueleto do projeto ★

**Faça.**
- Gere o projeto no Spring Initializr: Java 17, Maven, Spring Boot 3.x, dependências
  `Spring Web` e `Validation`. Group `br.senai.pa3` (ou o que o grupo preferir).
- Coloque na raiz do repositório (ou em `src/`, conforme a estrutura do README).
- Crie os pacotes vazios e entenda o papel de cada um:
  ```
  dominio/       records, enums e a calculadora (sem Spring)
  ingestao/      porta LeitorPlanilha e o adaptador POI
  aplicacao/     serviço que orquestra leitura + cálculo
  web/           controller, DTOs e tratamento de erro
  ```
- `mvn test` precisa passar. Commit: `chore: esqueleto do MVP`.

**Pergunte-se.** Por que `dominio` vem antes de `web` na ordem de construção?

**Traga.** A árvore de pastas e a saída do `mvn test`.

---

### [ ] T08 · Modelar o domínio ★

**Conceito: `BigDecimal` ou `double`?** Em `double`, `0.1 + 0.2` dá `0.30000000000000004`.
Numa comparação de fronteira (`|E| ≤ LEA`), esse resíduo pode reprovar uma peça boa.
Em metrologia, o resultado tem de ser reproduzível dígito a dígito: use `BigDecimal`,
crie sempre a partir de `String` (`new BigDecimal("0.1")`, nunca `new BigDecimal(0.1)`) e
compare com `compareTo`, não com `equals` (`2.0` e `2.00` não são `equals`).

**Faça.** Desenhe (primeiro no papel, depois em código):
- `record PontoEnsaio(String id, BigDecimal valorReferencia, BigDecimal lea, List<BigDecimal> leituras)`
- `record ResultadoPonto(...)`: o que precisa carregar para a tela e o relatório?
- `enum StatusPonto { DENTRO_DA_TOLERANCIA, FORA_DA_TOLERANCIA }`
- `enum Parecer { APROVADO, REPROVADO }`
- `record ResultadoCalibracao(...)`

**Perguntas antes de codar:**
- Que invariantes o construtor compacto do `PontoEnsaio` deve garantir? (leituras vazias?
  LEA negativo? LEA zero? `id` em branco?)
- A lista de leituras recebida deve ser copiada (`List.copyOf`)? Por quê?
- Com quantas casas decimais o resultado é exibido? Arredonda quando: no cálculo ou só na
  exibição? (Dica: arredondar antes de comparar com o LEA muda o resultado na fronteira.)

**Traga.** Os records/enums e as respostas.

---

### [ ] T09 · Média e erro sistemático com TDD ★

**Conceito: TDD.** Escreva o teste, veja falhar (vermelho), implemente o mínimo para passar
(verde), melhore o código (refatorar). O teste escrito antes obriga você a decidir o
comportamento antes da implementação.

**Exemplo-guia** (confira as contas antes de usar):

| Leituras | Referência | Média | Erro sistemático |
|---|---|---|---|
| 10,02 · 10,04 · 10,03 | 10,00 | 10,03 | +0,03 |
| 9,98 · 9,96 | 10,00 | 9,97 | −0,03 |

**Faça.**
- `CalculadoraMetrologicaTest` em JUnit 5: um teste por linha da tabela acima e um por ponto
  do seu oráculo da T06.
- Implemente `media(List<BigDecimal>)` e `erroSistematico(media, referencia)`.
- Divisão de `BigDecimal` sem `MathContext` lança `ArithmeticException` em dízimas
  (ex.: 10 / 3). Descubra isso escrevendo um teste com três leituras que gerem dízima.

**Traga.** Os testes (antes da implementação) e depois a implementação.

---

### [ ] T10 · Classificação por ponto e parecer final ★

**Conceito: a armadilha da lista vazia.** Em Java, `Stream.empty().allMatch(...)` retorna
`true`. Uma implementação ingênua de "aprovado se todos os pontos estão dentro" aprova uma
calibração **sem nenhum ponto**. Em medical device, isso é um defeito grave: ausência de
evidência não pode virar aprovação.

**Faça.** Testes primeiro:
- Ponto dentro, ponto fora, ponto na fronteira (conforme T04), erro negativo dentro.
- Parecer com todos dentro → `APROVADO`; um fora entre vários → `REPROVADO`.
- Lista vazia → o que acontece? (Decida: exceção? Esse caso nem chega aqui porque a
  ingestão rejeita antes? Defesa em profundidade sugere tratar nos dois lugares.)
- Implemente `classificar(PontoEnsaio)` e `calcular(List<PontoEnsaio>)`.

**Traga.** Testes + implementação + saída do `mvn test`.

---

### [ ] T11 · Caso de referência real ◇ 🔒

**Objetivo.** Provar que o motor reproduz um certificado verdadeiro (00091/2026) e mostrar
o "antes e depois" do AN-17: o valor que a planilha reprova indevidamente e o que o
sistema calcula.

**Cuidado.** Os valores medidos sozinhos não identificam pessoas, mas o grupo já classificou
os dados de cliente como confidenciais (RA-11). Use só os números, com identificador
fictício (`CAR-01`, `CAR-02`...), e decida se o teste vai para o repositório público ou
fica num arquivo local fora do Git.

**Traga.** A decisão e, se fizer, o teste.

---

## Fase 3 — Ingestão e API

### [ ] T12 · Leitor de planilha (porta + adaptador) ★

**Conceito.** `LeitorPlanilha` é uma interface do domínio (porta):
`List<PontoEnsaio> ler(InputStream)`. `LeitorPlanilhaPoi` é a implementação (adaptador).
Trocar POI por CSV, ou por exportação nativa do Calypso (PV-08), não toca no cálculo.

**⚠️ Dependência externa:** `org.apache.poi:poi-ooxml`. Confira a versão estável atual
no Maven Central antes de adicionar.

**Faça.**
- Leia a aba definida no contrato; localize as colunas **pelo cabeçalho**, não pela posição.
- Estude `DataFormatter` e `CellType`: célula numérica, texto, fórmula (o valor em cache
  serve?) e vazia se comportam de forma diferente.
- **Teste de risco, primeiro de tudo:** tente abrir a planilha real Strict localmente, num
  teste fora do Git. Registre o que o POI faz (exceção? qual?). Esse resultado vai para o
  relatório e justifica a regra do contrato.
- Testes com os arquivos `ok-*` da T06.

**Traga.** A interface, o adaptador e o resultado do teste com o arquivo Strict.

---

### [ ] T13 · Validação da entrada e limites 🔒 ★

**Conceito.** Validar tudo e devolver **todos** os erros de uma vez (não parar no primeiro)
economiza idas e vindas do operador. Cada erro precisa dizer onde: aba, linha, coluna e
motivo.

**Faça.**
- `PlanilhaInvalidaException` carregando uma lista de `ErroValidacao(linha, coluna, motivo)`.
- Cubra os arquivos `inv-*` da T06.
- Limites de segurança:
  - tamanho máximo do upload (`spring.servlet.multipart.max-file-size`);
  - número máximo de linhas processadas;
  - proteção contra zip bomb (estude `ZipSecureFile` do POI);
  - aceitar só `.xlsx`; não executar nem avaliar macros.
- A mensagem para o usuário não expõe stack trace nem caminho do servidor.

**Pergunte-se.** Por que validar a extensão do arquivo não basta? (O que é *magic number*?)

**Traga.** A exceção, o validador e os testes dos inválidos passando.

---

### [ ] T14 · Endpoint de processamento ★

**Faça.**
- `ProcessamentoService` (pacote `aplicacao`): recebe o `InputStream`, chama o leitor e a
  calculadora, devolve `ResultadoCalibracao`.
- `POST /api/calibracoes/processamento` com `multipart/form-data` (campo `arquivo`).
- Resposta em JSON por DTO, não pelo record de domínio direto. Por quê? (Dica: o que
  acontece com o contrato da API se o domínio mudar?)
- Teste com `curl -F "arquivo=@ok-aprovado.xlsx" localhost:8080/api/calibracoes/processamento`
  e com `@WebMvcTest` ou `MockMvc`.

**Traga.** O controller, o DTO e a saída do `curl`.

---

### [ ] T15 · Tratamento global de erros 🔒 ★

**Conceito.** O Spring 6 traz `ProblemDetail` (RFC 9457): um formato padrão de erro em JSON.

**Faça.** `@RestControllerAdvice` mapeando:

| Situação | HTTP | Corpo |
|---|---|---|
| Planilha inválida | 400 | `ProblemDetail` + lista de erros (linha/coluna/motivo) |
| Arquivo maior que o limite | 413 | Mensagem com o limite |
| Tipo de arquivo não aceito | 415 | Formatos aceitos |
| Erro inesperado | 500 | Mensagem genérica + id de correlação; detalhe só no log |

**Logs e LGPD.** Registre o hash SHA-256 e o tamanho do arquivo, nunca o conteúdo. Se o
contrato tiver nome de técnico, ele não vai para o log.

**Traga.** O advice e um exemplo de resposta 400.

---

### [ ] T16 · Interface web ★

**Objetivo.** O objetivo da entrega (seção 1 do roteiro, não um item numerado) pede os
resultados e o parecer "numa interface limpa e intuitiva": enviar o arquivo, ver a tabela
por ponto e o parecer.

**Faça.** `src/main/resources/static/index.html` com HTML, CSS e JS puro (`fetch`):
- Campo de arquivo + botão; estado de carregamento; mensagem de erro legível (usa a lista
  de erros da T15).
- Tabela: ponto, leituras, média, referência, erro, LEA, status.
- Parecer final em destaque.
- **Acessibilidade:** o status aparece como **texto e cor**, nunca só cor (daltonismo;
  RNF-15 da AV01 cita WCAG 2.1 AA). Use `<table>` semântica com `<th scope="col">`.
- Mostre os enums com os nomes exatos do roteiro (pode acrescentar rótulo amigável ao lado).

**Traga.** Um print da tela com `ok-reprovado.xlsx` processado.

---

### [ ] T17 · Persistir o processamento ◇

**Objetivo.** Reforçar o "transacional": cada processamento vira um registro consultável,
com idempotência por hash (RN-20 da AV01: reenviar o mesmo arquivo não gera novo registro).

**Opção leve:** H2 em memória + Spring Data JPA (dependências externas: avise no
relatório). `GET /api/calibracoes/{id}` devolve o resultado salvo.

**Traga.** A decisão (fazer ou deixar para a AV04) e, se fizer, o modelo da tabela.

---

## Fase 4 — Evidências e relatório

### [ ] T18 · Roteiro de testes e evidências ★

**Faça.** `docs/av03/evidencias.md` com uma linha por caso:

| ID | Requisito | Arquivo | Esperado | Obtido | Evidência |
|---|---|---|---|---|---|
| CT-01 | Item 1.2 (parecer) · RF-08 | `ok-aprovado.xlsx` | `APROVADO` | | print |

- Inclua todos os arquivos da T06, os testes automatizados (print do `mvn test` ou do
  relatório do Surefire) e os prints da tela.
- Guarde os prints em `evidencias/av03/`.

**Traga.** A tabela preenchida.

---

### [ ] T19 · README e repositório público ★

**Faça.**
- README: o que é, como rodar (`mvn spring-boot:run`, endereço da tela), como rodar os
  testes, estrutura de pastas, aviso sobre dados sintéticos, status das entregas atualizado.
- Clone o repositório numa pasta limpa e siga o seu próprio README do zero. Se travar, o
  avaliador também vai travar.
- Torne o repositório público (só depois da T03 resolvida).

**Traga.** O link e o resultado do teste de clone limpo.

---

### [ ] T20 · Relatório em PDF ★

**Estrutura sugerida** (DOCX → PDF, como nas anteriores):

1. Introdução e vínculo com AV01 e AV02 (o que vem de cada uma).
2. Arquitetura do MVP: diagrama núcleo + adaptadores; D-011 e D-012; como o mesmo núcleo
   roda na `fn-ingestao`.
3. Contrato da planilha (T05) e decisão sobre OOXML Strict, com o resultado da T12.
4. Regras implementadas: fórmulas, critério de fronteira, `BigDecimal`, lista vazia.
5. Validação e segurança da entrada: limites, erros, LGPD nos logs e nos dados de teste.
6. Evidências (T18).
7. Rastreabilidade: item do roteiro → RF/RN da AV01 → classe → teste.
8. Limitações e próximos passos: incerteza, k e veff (R-46 a R-50), PDF do certificado,
   autenticação, nuvem.
9. Premissas `[PREMISSA]` e pontos `[VALIDAR]`.
10. Link do repositório.

**Traga.** O sumário e uma seção por vez, se quiser revisão.

---

### [ ] T21 · Revisão final ★

- [ ] Os quatro enums aparecem com os nomes exatos no código, na API e na tela
- [ ] `mvn test` verde num clone limpo
- [ ] Nenhum arquivo real da Schulz no histórico do Git
- [ ] Repositório público e link no relatório
- [ ] Todo item do roteiro tem evidência no relatório
- [ ] Premissas declaradas (Item 2, se não houver resposta)
- [ ] Revisão cruzada: quem escreveu uma seção não a revisa
- [ ] PDF exportado com um dia de folga

---

## Registro de progresso

| Tarefa | Responsável | Status | Observação |
|---|---|---|---|
| T00 | Rodrigo | Concluída | Prazo 15/10; Item 2 ignorado até resposta |
| T01 | | | |
| T02 | | | |
| T03 | | | |
| T04 | | | |
| T05 | | | |
| T06 | | | |
| T07 | | | |
| T08 | | | |
| T09 | | | |
| T10 | | | |
| T11 | | | |
| T12 | | | |
| T13 | | | |
| T14 | | | |
| T15 | | | |
| T16 | | | |
| T17 | | | |
| T18 | | | |
| T19 | | | |
| T20 | | | |
| T21 | | | |
