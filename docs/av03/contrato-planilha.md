# Contrato da planilha de entrada — MVP AV03

## 1. Formato do arquivo
.xlsx Transitional; Strict rejeitado devido a suporte limitado das bibliotecas (evidência na T12). O arquivo deverá ser salvo como Pasta de Trabalho do Excel (.xlsx).

## 2. Aba
A aba se chamará `Medicoes` — outras abas, se houver, serão ignoradas.

## 3. Colunas

| Cabeçalho | Tipo | Unidade | Obrigatória | Regra |
|---|---|---|---|---|
| Caracteristica | texto | — | sim | única na planilha |
| ValorReferencia | número | mm | sim | |
| Leitura1, Leitura2… | número | mm | sim | ver §4 |
| ToleranciaSuperior | número | mm | sim | maior que zero; ver §5 |
| ToleranciaInferior | número | mm | sim | ver §5 |

Planilhas com a coluna Desvio serão rejeitadas: valor calculado não é aceito na entrada (D-T04-4).

## 4. Leituras
Cabeçalho com pelo menos duas colunas `Leitura*`, todas obrigatórias em todas as linhas. Isso garante o mesmo número de leituras por ponto e o mínimo de duas. [VALIDAR com a Schulz: o mínimo de duas é exigência do procedimento ITL-0026?]

## 5. Tolerância
Dois tipos de tolerância serão aceitos:
- bilateral simétrica (Tol− = −Tol+);
- unilateral (Tol− = 0).

Em ambos, LEA = Tol+ (D-T04-2).

## 6. Regras de rejeição

Erros de arquivo e de estrutura interrompem a validação na hora, porque sem a aba ou o cabeçalho não há o que ler. Os erros de linha são todos coletados e devolvidos juntos.

**Arquivo**

- R01 — Arquivo maior que 5 MB: "O arquivo excede o limite de 5 MB."
- R02 — Formato de arquivo diferente de xlsx: "O arquivo não está em formato xlsx. Salve como 'Pasta de Trabalho do Excel (.xlsx)'."
- R03 — Formato de arquivo Strict: "O arquivo está em formato OOXML Strict. Salve como 'Pasta de Trabalho do Excel (.xlsx)'."

**Estrutura**

- R04 — Aba Medicoes ausente: "A planilha fornecida não contém a aba Medicoes."
- R05 — Cabeçalho ausente: "O cabeçalho {cabecalho} não consta na planilha fornecida."
- R06 — Menos de duas colunas Leitura*: "A planilha fornecida não contém duas séries de medições com cabeçalho Leitura* (Leitura1, Leitura2)."
- R07 — Planilha com coluna Desvio: "A planilha contém a coluna Desvio, que não é utilizada neste cálculo. Remova a coluna e carregue novamente o arquivo."
- R08 — Planilha sem linhas de dados: "A planilha fornecida não contém dados."
- R09 — Mais de 10 mil linhas: "A planilha excede o limite de 10 mil linhas."

**Linha** (todas coletadas)

- R10 — Item obrigatório não preenchido: "Linha {n}, coluna {coluna}: valor obrigatório ausente."
- R11 — Dado numérico inválido: "Linha {n}, coluna {coluna}: '{valor}' não é um número válido."
- R12 — Característica repetida: "Característica '{nome}' repetida nas linhas {n1} e {n2}."
- R13 — Tolerância superior zero ou negativa: "Linha {n} ('{nome}'): a tolerância superior precisa ser maior que zero."
- R14 — Tolerância fora dos dois casos da §5: "Linha {n} ('{nome}'): tolerância +{sup}/{inf} fora do escopo do MVP — aceitas: bilateral simétrica ou unilateral."
- R15 — Leitura negativa em característica unilateral: "Linha {n} ('{nome}'): leitura negativa em característica unilateral — verifique a medição."

## 7. Exemplo (dados fictícios)

| Caracteristica | ValorReferencia | Leitura1 | Leitura2 | ToleranciaSuperior | ToleranciaInferior |
|---|---|---|---|---|---|
| Diametro eixo | 0,06 | 0,0602 | 0,0604 | 0,005 | -0,005 |
| Cpto eixo | 2,83 | 2,8299 | 2,8302 | 0,004 | -0,004 |
| Largura base | 0,9288 | 0,9286 | 0,9290 | 0,007 | -0,007 |
| Planicidade base | 0 | 0,0021 | 0,0023 | 0,05 | 0 |

## 8. Relação com a AV02
Este contrato fecha a [PREMISSA] do §3.1.2 da AV02 ("o layout exato de abas e colunas é fixado como contrato na Entrega 03"). A mesma planilha é a entrada da `fn-ingestao` no pipeline em nuvem. Com este formato de contrato e validação, não há erro silencioso nem dado perdido: a planilha é validada na sua totalidade ou rejeitada no ato do upload.
