# Oráculo das planilhas sintéticas — AV03 (T06)

Gerado por `ferramentas/gerar_planilhas.py` com `decimal.Decimal` (precisão 28), independente da implementação Java. Média e erro em **precisão completa**, sem arredondamento. LEA = ToleranciaSuperior (D-T04-2); `|E| ≤ LEA` → `DENTRO_DA_TOLERANCIA` (D-T04-1). Linhas no padrão do Excel (cabeçalho = 1).

## Cenários válidos

### `ok-aprovado.xlsx` → `APROVADO`

Todos os pontos dentro, com folga

| Característica | Leituras | Referência | LEA | Média | Erro | Status |
|---|---|---|---|---|---|---|
| Diâmetro eixo | 4,13 · 4,14 | 4,12 | 0,03 | 4,135 | 0,015 | DENTRO_DA_TOLERANCIA |
| Comprimento eixo | 10,807 · 10,811 | 10,8 | 0,02 | 10,809 | 0,009 | DENTRO_DA_TOLERANCIA |
| Largura base | 0,9286 · 0,929 | 0,9288 | 0,007 | 0,9288 | 0 | DENTRO_DA_TOLERANCIA |

### `ok-reprovado.xlsx` → `REPROVADO`

Um ponto fora entre quatro

| Característica | Leituras | Referência | LEA | Média | Erro | Status |
|---|---|---|---|---|---|---|
| Diâmetro furo A | 6,01 · 6,02 | 6 | 0,03 | 6,015 | 0,015 | DENTRO_DA_TOLERANCIA |
| Diâmetro furo B | 6,05 · 6,07 | 6 | 0,03 | 6,06 | 0,06 | FORA_DA_TOLERANCIA |
| Altura flange | 25,39 · 25,41 | 25,4 | 0,05 | 25,4 | 0 | DENTRO_DA_TOLERANCIA |
| Planicidade face | 0,012 · 0,014 | 0 | 0,05 | 0,013 | 0,013 | DENTRO_DA_TOLERANCIA |

### `ok-limite.xlsx` → `APROVADO`

|E| = LEA exato, lados positivo e negativo; em double os dois dão FORA

| Característica | Leituras | Referência | LEA | Média | Erro | Status |
|---|---|---|---|---|---|---|
| Comprimento pino | 10,07 · 10,09 | 10 | 0,08 | 10,08 | 0,08 | DENTRO_DA_TOLERANCIA |
| Diâmetro bucha | 1,94 · 1,96 | 2 | 0,05 | 1,95 | −0,05 | DENTRO_DA_TOLERANCIA |

### `ok-erro-negativo.xlsx` → `APROVADO`

Média abaixo da referência, ainda dentro (testa o abs)

| Característica | Leituras | Referência | LEA | Média | Erro | Status |
|---|---|---|---|---|---|---|
| Diâmetro alojamento | 19,97 · 19,98 | 20 | 0,03 | 19,975 | −0,025 | DENTRO_DA_TOLERANCIA |
| Espessura chapa | 3,49 · 3,49 | 3,5 | 0,02 | 3,49 | −0,01 | DENTRO_DA_TOLERANCIA |

### `ok-unilateral.xlsx` → `REPROVADO`

Tol− = 0: um dentro, um fora e uma leitura igual a zero (aceita, R15 é só < 0)

| Característica | Leituras | Referência | LEA | Média | Erro | Status |
|---|---|---|---|---|---|---|
| Planicidade base | 0,02 · 0,03 | 0 | 0,05 | 0,025 | 0,025 | DENTRO_DA_TOLERANCIA |
| Posição furo | 0,11 · 0,13 | 0 | 0,1 | 0,12 | 0,12 | FORA_DA_TOLERANCIA |
| Paralelismo face | 0 · 0,004 | 0 | 0,01 | 0,002 | 0,002 | DENTRO_DA_TOLERANCIA |

### `ok-arredondamento.xlsx` → `REPROVADO`

|E| = 0,00045 > LEA; arredondar a média a 4 casas antes de comparar aprovaria

| Característica | Leituras | Referência | LEA | Média | Erro | Status |
|---|---|---|---|---|---|---|
| Diâmetro pino guia | 0,0604 · 0,0605 | 0,06 | 0,0004 | 0,06045 | 0,00045 | FORA_DA_TOLERANCIA |
| Diâmetro furo guia | 0,0595 · 0,0596 | 0,06 | 0,0004 | 0,05955 | −0,00045 | FORA_DA_TOLERANCIA |

### `ok-tres-leituras.xlsx` → `REPROVADO`

Três leituras com média em dízima; o segundo ponto fica fora por 0,00033…

| Característica | Leituras | Referência | LEA | Média | Erro | Status |
|---|---|---|---|---|---|---|
| Comprimento haste | 10,01 · 10,02 · 10,02 | 10 | 0,02 | 10,01666666666666666666666667 (dízima) | 0,01666666666666666666666667 | DENTRO_DA_TOLERANCIA |
| Diâmetro haste | 1 · 1 · 1,01 | 1 | 0,003 | 1,003333333333333333333333333 (dízima) | 0,003333333333333333333333333 | FORA_DA_TOLERANCIA |

## Cenários inválidos

| Arquivo | Regra | Cenário | Erros esperados |
|---|---|---|---|
| `inv-R04-sem-aba.xlsx` | R04 | Aba chamada `Medicao` (sem o s) | Aba Medicoes ausente |
| `inv-R05-sem-tolsup.xlsx` | R05 | Sem a coluna ToleranciaSuperior | Cabeçalho ToleranciaSuperior ausente |
| `inv-R06-uma-leitura.xlsx` | R06 | Só Leitura1 | Menos de duas colunas Leitura* |
| `inv-R07-desvio.xlsx` | R07 | Coluna Desvio presente | Coluna Desvio presente |
| `inv-R08-vazia.xlsx` | R08 | Só o cabeçalho | Nenhuma linha de dados |
| `inv-R10-obrigatorio.xlsx` | R10 | Leitura2 vazia na linha 3 | Linha 3, Leitura2: valor obrigatório ausente |
| `inv-R11-texto.xlsx` | R11 | Texto 'abc' na Leitura1 | Linha 2, Leitura1: 'abc' não é número |
| `inv-R12-repetida.xlsx` | R12 | Diâmetro eixo nas linhas 2 e 5 | Característica repetida nas linhas 2 e 5 |
| `inv-R13-tolsup-zero.xlsx` | R13 | Tol+ = 0 e Tol− = 0 na linha 4 | Linha 4: tolerância superior precisa ser > 0 |
| `inv-R14-assimetrica.xlsx` | R14 | +0,05 / −0,02 na linha 2 | Linha 2: tolerância fora do escopo |
| `inv-R15-negativa-unilateral.xlsx` | R15 | Leitura1 = −0,002 com Tol− = 0 na linha 5 | Linha 5: leitura negativa em característica unilateral |
| `inv-multiplos-erros.xlsx` | R10, R11, R14 | Um erro diferente em cada linha (2, 3 e 4) | Linha 2, Leitura2: valor obrigatório ausente<br>Linha 3, Leitura1: 'n/a' não é número<br>Linha 4: tolerância fora do escopo |
| `inv-R02-nao-xlsx.xlsx` | R02 | Arquivo de texto (CSV) com extensão .xlsx | Formato diferente de xlsx |
| `inv-R03-strict.xlsx` | R03 | `ok-aprovado` salvo como Strict no Excel desktop (não gerado por este script) | Formato OOXML Strict |

R01 (5 MB) e R09 (10 mil linhas) são gerados dentro dos testes, não versionados.
