# Roteiro de testes e evidências — AV03 (T18)

## Ambiente

| Item               | Valor                                                                                                                |
| ------------------ | -------------------------------------------------------------------------------------------------------------------- |
| Commit             | `86857e9` (`86857e960812ee716d0829d8ebbcdba42257a5f8`), sem alterações locais em `src/`                              |
| Data da coleta     | 11/10/2026, 23:33 (UTC−3): respostas da API às 23:33:36–37, `./mvnw test` às 23:33:45; prints da tela às 23:46–23:57 |
| Sistema            | Ubuntu 24.04.4 LTS (kernel Linux 7.0.0-31)                                                                           |
| JDK                | Eclipse Temurin 21.0.10 (compilação com `java.version` 17)                                                           |
| Build              | Maven 3.9.6 pelo wrapper (`./mvnw`)                                                                                  |
| Framework          | Spring Boot 4.1.1                                                                                                    |
| Leitura do `.xlsx` | Apache POI `poi-ooxml` 5.5.1                                                                                         |
| Servidor           | `./mvnw spring-boot:run`, `http://localhost:8080`                                                                    |
| Cliente HTTP       | curl 8.5.0                                                                                                           |
| Navegador (prints) | Google Chrome Versão 152.0.7977.82 (Build Oficial) (x86_64) para Linux                                               |
| Planilhas          | Sintéticas, de `ferramentas/gerar_planilhas.py`; esperado do `oraculo-planilhas.md`                                  |

Linha de base única: todas as respostas e o `mvn-test.txt` foram gravados de uma vez, nesse
commit. As planilhas de 5 MB (`exato-5mb.xlsx`, `limite.xlsx`) foram geradas localmente e não
são versionadas.

**Como reproduzir as respostas da API** (servidor no ar):

```bash
URL=http://localhost:8080/api/calibracoes/processamento
for f in src/test/resources/planilhas/*.xlsx; do
  n=$(basename "$f" .xlsx)
  { echo "\$ curl -i -F arquivo=@$n.xlsx $URL"; curl -sS -i -F arquivo=@"$f" "$URL"; } \
    > "docs/av03/evidencias/respostas/$n.txt" 2>&1
done
```

## Casos de teste

Arquivos de evidência relativos a `docs/av03/evidencias/`. O Esperado vem do oráculo e do
contrato; o Obtido é o valor lido no arquivo de evidência, com vírgula no lugar do ponto e as
5 casas de média e erro como saem no JSON. Linha e coluna `null` são erros gerais do arquivo.

### Cálculo e parecer (planilhas válidas)

| ID    | Requisito                                   | Arquivo                  | Esperado                                                                              | Obtido                                                                                                                                             | Resultado | Evidência                         |
| ----- | ------------------------------------------- | ------------------------ | ------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- | --------- | --------------------------------- |
| CT-01 | Item 1.2 (média, erro, status, parecer)     | `ok-aprovado.xlsx`       | 200 · `APROVADO`; 3 pontos `DENTRO_DA_TOLERANCIA`; médias 4,135 · 10,809 · 0,9288     | 200 · `APROVADO` · médias 4,13500 · 10,80900 · 0,92880 · E = 0,01500 · 0,00900 · 0,00000 · 3 × `DENTRO_DA_TOLERANCIA`                              | Passou    | `respostas/ok-aprovado.txt`       |
| CT-02 | Item 1.2 (parecer)                          | `ok-reprovado.xlsx`      | 200 · `REPROVADO`; só Diâmetro furo B `FORA_DA_TOLERANCIA` (E = 0,06 > LEA 0,03)      | 200 · `REPROVADO` · Diâmetro furo B: E = 0,06000, LEA 0,03 · `FORA_DA_TOLERANCIA`; furo A, Altura flange e Planicidade face `DENTRO_DA_TOLERANCIA` | Passou    | `respostas/ok-reprovado.txt`      |
| CT-03 | Item 1.2 (\|erro\| vs LEA) · D-T04-1        | `ok-limite.xlsx`         | 200 · `APROVADO`; \|E\| = LEA nos dois sinais (0,08 e −0,05) → `DENTRO_DA_TOLERANCIA` | 200 · `APROVADO` · E = 0,08000 e −0,05000 · LEA 0,08 e 0,05 · `DENTRO_DA_TOLERANCIA`                                                               | Passou    | `respostas/ok-limite.txt`         |
| CT-04 | Item 1.2 (\|erro\| vs LEA)                  | `ok-erro-negativo.xlsx`  | 200 · `APROVADO`; E = −0,025 e −0,01, ambos `DENTRO_DA_TOLERANCIA`                    | 200 · `APROVADO` · E = −0,02500 e −0,01000 · `DENTRO_DA_TOLERANCIA`                                                                                | Passou    | `respostas/ok-erro-negativo.txt`  |
| CT-05 | Item 1.2 · D-T04-2 · R15 (leitura 0 aceita) | `ok-unilateral.xlsx`     | 200 · `REPROVADO`; Posição furo `FORA_DA_TOLERANCIA` (0,12 > 0,1); leitura 0 aceita   | 200 · `REPROVADO` · Posição furo: E = 0,12000, LEA 0,1 · `FORA_DA_TOLERANCIA`; Paralelismo face (leitura 0): E = 0,00200 · `DENTRO_DA_TOLERANCIA`  | Passou    | `respostas/ok-unilateral.txt`     |
| CT-06 | Item 1.2 · D-011 (`BigDecimal`)             | `ok-arredondamento.xlsx` | 200 · `REPROVADO`; E = 0,00045 e −0,00045 > LEA 0,0004 → `FORA_DA_TOLERANCIA`         | 200 · `REPROVADO` · E = 0,00045 e −0,00045 · LEA 0,00040 · `FORA_DA_TOLERANCIA` nos dois                                                           | Passou    | `respostas/ok-arredondamento.txt` |
| CT-07 | Item 1.1 (N leituras) · Item 1.2            | `ok-tres-leituras.xlsx`  | 200 · `REPROVADO`; médias 10,01667 e 1,00333; Diâmetro haste `FORA_DA_TOLERANCIA`     | 200 · `REPROVADO` · médias 10,01667 e 1,00333 · Diâmetro haste: E = 0,00333, LEA 0,003 · `FORA_DA_TOLERANCIA`                                      | Passou    | `respostas/ok-tres-leituras.txt`  |

### Validação da entrada (planilhas inválidas)

| ID    | Requisito                | Arquivo                            | Esperado                                                          | Obtido                                                                                                 | Resultado | Evidência                                   |
| ----- | ------------------------ | ---------------------------------- | ----------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ | --------- | ------------------------------------------- |
| CT-08 | Item 1.1 · R02           | `inv-R02-nao-xlsx.xlsx`            | 400 · R02                                                         | 400 · R02 · linha e coluna `null`                                                                      | Passou    | `respostas/inv-R02-nao-xlsx.txt`            |
| CT-09 | Item 1.1 · R03           | `inv-R03-strict.xlsx`              | 400 · R03                                                         | 400 · R03 · linha e coluna `null`                                                                      | Passou    | `respostas/inv-R03-strict.txt`              |
| CT-10 | Item 1.1 · R04           | `inv-R04-sem-aba.xlsx`             | 400 · R04                                                         | 400 · R04 · linha e coluna `null`                                                                      | Passou    | `respostas/inv-R04-sem-aba.txt`             |
| CT-11 | Item 1.1 · R05           | `inv-R05-sem-tolsup.xlsx`          | 400 · R05, coluna ToleranciaSuperior                              | 400 · R05 · linha `null` · ToleranciaSuperior                                                          | Passou    | `respostas/inv-R05-sem-tolsup.txt`          |
| CT-12 | Item 1.1 · R06           | `inv-R06-uma-leitura.xlsx`         | 400 · R06                                                         | 400 · R06 · linha e coluna `null`                                                                      | Passou    | `respostas/inv-R06-uma-leitura.txt`         |
| CT-13 | Item 1.1 · R07 · D-T04-4 | `inv-R07-desvio.xlsx`              | 400 · R07                                                         | 400 · R07 · linha e coluna `null`                                                                      | Passou    | `respostas/inv-R07-desvio.txt`              |
| CT-14 | Item 1.1 · R08           | `inv-R08-vazia.xlsx`               | 400 · R08                                                         | 400 · R08 · linha e coluna `null`                                                                      | Passou    | `respostas/inv-R08-vazia.txt`               |
| CT-15 | Item 1.1 · R10           | `inv-R10-obrigatorio.xlsx`         | 400 · R10, linha 3, Leitura2                                      | 400 · R10 · linha 3 · Leitura2                                                                         | Passou    | `respostas/inv-R10-obrigatorio.txt`         |
| CT-16 | Item 1.1 · R11           | `inv-R11-texto.xlsx`               | 400 · R11, linha 2, Leitura1                                      | 400 · R11 · linha 2 · Leitura1                                                                         | Passou    | `respostas/inv-R11-texto.txt`               |
| CT-17 | Item 1.1 · R12           | `inv-R12-repetida.xlsx`            | 400 · R12, linha 5, Caracteristica                                | 400 · R12 · linha 5 · Caracteristica                                                                   | Passou    | `respostas/inv-R12-repetida.txt`            |
| CT-18 | Item 1.1 · R13 · D-T04-2 | `inv-R13-tolsup-zero.xlsx`         | 400 · R13, linha 4, ToleranciaSuperior                            | 400 · R13 · linha 4 · ToleranciaSuperior                                                               | Passou    | `respostas/inv-R13-tolsup-zero.txt`         |
| CT-19 | Item 1.1 · R14 · D-T04-2 | `inv-R14-assimetrica.xlsx`         | 400 · R14, linha 2, ToleranciaInferior                            | 400 · R14 · linha 2 · ToleranciaInferior                                                               | Passou    | `respostas/inv-R14-assimetrica.txt`         |
| CT-20 | Item 1.1 · R15 · D-T04-3 | `inv-R15-negativa-unilateral.xlsx` | 400 · R15, linha 5, Leitura1                                      | 400 · R15 · linha 5 · Leitura1                                                                         | Passou    | `respostas/inv-R15-negativa-unilateral.txt` |
| CT-21 | Item 1.1 · R10, R11, R14 | `inv-multiplos-erros.xlsx`         | 400 · 3 erros juntos: R10 (linha 2), R11 (linha 3), R14 (linha 4) | 400 · 3 erros · R10 · linha 2 · Leitura2; R11 · linha 3 · Leitura1; R14 · linha 4 · ToleranciaInferior | Passou    | `respostas/inv-multiplos-erros.txt`         |

### Limites e protocolo do upload

| ID    | Requisito                   | Arquivo                             | Esperado                                            | Obtido                                                                                                              | Resultado | Evidência             |
| ----- | --------------------------- | ----------------------------------- | --------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- | --------- | --------------------- |
| CT-22 | Item 1.1 · R01              | `exato-5mb.xlsx` (5 242 880 bytes)  | Passa da R01; 400 · R02 (conteúdo não é xlsx)       | 400 · R02 · linha e coluna `null`                                                                                   | Passou    | `400-exato-5mb.txt`   |
| CT-23 | Item 1.1 · R01              | `limite.xlsx` (5 242 881 bytes)     | 413 · "O arquivo excede o limite de 5 MB."          | 413 · detail "O arquivo excede o limite de 5 MB."                                                                   | Passou    | `413-limite.txt`      |
| CT-24 | Item 1.1 (upload multipart) | Corpo JSON `{}`                     | 415 · cabeçalho `Accept: multipart/form-data`       | 415 · `Accept: multipart/form-data` · detail "Envie a planilha .xlsx como multipart/form-data, no campo 'arquivo'." | Passou    | `415-json.txt`        |
| CT-25 | Item 1.1 (upload multipart) | `ok-aprovado.xlsx` no campo `outro` | 400 · detail em português pedindo o campo `arquivo` | 400 · detail "Envie a planilha no campo 'arquivo' (multipart/form-data)."                                           | Passou    | `400-sem-arquivo.txt` |

### Testes automatizados

O Obtido destas linhas vem do relatório do Surefire da execução registrada em `mvn-test.txt`.

| ID    | Requisito                                         | Arquivo                                                    | Esperado                                                                                               | Obtido                                                                                                  | Resultado | Evidência                                                                             |
| ----- | ------------------------------------------------- | ---------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------- | --------- | ------------------------------------------------------------------------------------- |
| CT-26 | Item 1.1 · R09                                    | Gerada no teste                                            | 10 001 linhas → R09; 10 000 passam; linhas só formatadas não contam; linha solta além de 100 000 → R09 | 4 de 4 testes `r09_*` sem falha                                                                         | Passou    | `LeitorPlanilhaPoiInvalidosTest.r09_*` · `mvn-test.txt`                               |
| CT-27 | Item 1.1 · R02 · D-013                            | —                                                          | `ZipSecureFile` com limite de 52 428 800 bytes (50 MB) por entrada                                     | 52 428 800 bytes · 1 de 1 teste sem falha. O caminho da rejeição não é testado (D-013)                  | Passou    | `LeitorPlanilhaPoiInvalidosTest.zipSecureFile_limiteDeEntradaDe50MB` · `mvn-test.txt` |
| CT-28 | Segurança e LGPD (T15): detalhe técnico só no log | Falha plantada com o caminho `/srv/app/segredo/config.yml` | 500 · `application/problem+json` · `id` UUID · corpo sem `Exception`, sem `at br.` e sem o caminho     | 500 · `application/problem+json` · `id` UUID · nenhum dos três textos no corpo · 1 de 1 teste sem falha | Passou    | `ErroInesperadoTest.falhaInesperada_500ComIdESemDetalheTecnico` · `mvn-test.txt`      |
| CT-29 | Critério de avaliação (evidências de teste)       | Suíte completa (`./mvnw test`)                             | 71 testes, 0 falhas, 0 erros                                                                           | 71 testes · 0 falhas · 0 erros · 0 ignorados · BUILD SUCCESS                                            | Passou    | `mvn-test.txt`                                                                        |

### Tela

| ID    | Requisito                                           | Arquivo                                                       | Esperado                                                                                    | Obtido                                                                                                                                                                                                                                                                        | Resultado | Evidência                                                        |
| ----- | --------------------------------------------------- | ------------------------------------------------------------- | ------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------- | ---------------------------------------------------------------- |
| CT-30 | Objetivo da entrega (interface) · Item 1.2 · RNF-15 | `ok-reprovado.xlsx`                                           | Tabela por ponto; status com o enum exato, rótulo e cor; parecer `REPROVADO` em destaque    | Tabela com os 4 pontos (leituras, média, referência, erro, LEA); `FORA_DA_TOLERANCIA` "Fora da tolerância" em vermelho no furo B, `DENTRO_DA_TOLERANCIA` "Dentro da tolerância" em verde nos outros 3; quadro "Parecer final" vermelho com `REPROVADO` "Calibração reprovada" | Passou    | `tela/ok-reprovado.png`                                          |
| CT-31 | Objetivo da entrega (interface) · Item 1.1 · RNF-15 | `inv-multiplos-erros.xlsx`                                    | Detail e tabela linha/coluna/regra/mensagem com R10, R11 e R14                              | Detail "A planilha não cumpre o contrato. Corrija os erros listados e envie de novo."; tabela com 3 linhas: 2 · Leitura2 · R10; 3 · Leitura1 · R11; 4 · ToleranciaInferior · R14, cada uma com a mensagem da API                                                              | Passou    | `tela/inv-multiplos-erros.png`                                   |
| CT-32 | Objetivo da entrega (interface) · R01 · RNF-15      | `limite.xlsx`                                                 | Mensagem "O arquivo excede o limite de 5 MB."                                               | Mensagem "O arquivo excede o limite de 5 MB.", sem parecer nem tabela                                                                                                                                                                                                         | Passou    | `tela/413-limite.png`                                            |
| CT-33 | Objetivo da entrega (interface limpa)               | `ok-limite.xlsx`, depois `ok-tres-leituras.xlsx` só escolhido | Ao escolher o segundo arquivo, antes de enviar, parecer, tabela de pontos e mensagens somem | Antes: `ok-limite.xlsx` processado, com mensagem, parecer `APROVADO` e tabela de 2 pontos. Depois de escolher `ok-tres-leituras.xlsx`, sem enviar: só o formulário na tela                                                                                                    | Passou    | `tela/troca-arquivo-antes.png` · `tela/troca-arquivo-depois.png` |
