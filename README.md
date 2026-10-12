# Projeto Aplicado III — Certificados de Calibração

**Automação da emissão de certificados de calibração — Schulz S.A.**
UniSENAI/SC · 2026.2 · Prof. MSc. Hugo Menezes Barra

Autor: Rodrigo Mainardes Ribeiro

## O problema

A Schulz Metrologia leva ~13 dias entre medir um dispositivo na MMC e emitir o certificado
de calibração. O caminho hoje é uma cadeia de planilhas Excel acopladas por cópia manual:
28 valores transportados à mão por certificado, cálculo não validado, sem trilha de auditoria.
O diagnóstico completo é o da AV01, que não está neste repositório porque cita arquivos reais
da empresa.

## O que o MVP faz (AV03)

Aplicação web que recebe a planilha de medições (`.xlsx`), valida o arquivo contra o
[contrato da planilha](docs/av03/contrato-planilha.md) (regras R01 a R15) e calcula, para cada
ponto de ensaio:

- a **média** das leituras;
- o **erro sistemático** (média − valor de referência);
- o **status**: `DENTRO_DA_TOLERANCIA` se |erro| ≤ LEA, senão `FORA_DA_TOLERANCIA`.

O **parecer** é `APROVADO` se todos os pontos estão dentro da tolerância e `REPROVADO` se algum
não está. O resultado aparece na tela e na API (JSON).

Os cálculos usam `BigDecimal`: média com escala 10 (`HALF_EVEN`), comparação com o LEA sem
arredondamento prévio; média e erro são exibidos com 5 casas.

Stack: Java 17, Spring Boot 4.1, Apache POI 5.5 ([D-011](docs/decisoes.md)). O núcleo de
cálculo e de leitura não depende do Spring ([D-012](docs/decisoes.md)), para rodar também na
função serverless da arquitetura da AV02.

## Requisitos

- JDK 17 ou superior (`java -version`).
- Maven não precisa estar instalado: o projeto usa o Maven Wrapper (`mvnw`). O primeiro build
  baixa as dependências e precisa de internet.

## Como rodar

```bash
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

Abra <http://localhost:8080/>, escolha uma planilha de `src/test/resources/planilhas/`
(por exemplo, `ok-reprovado.xlsx`) e clique em **Processar**.

## Como testar

```bash
./mvnw test                     # Windows: mvnw.cmd test
```

Resultado esperado num clone limpo: `Tests run: 71, Failures: 0, Errors: 0, Skipped: 1`.
O teste pulado (`PoiStrictTest.xssfWorkbookRecusaStrictReal`) usa uma planilha real da
empresa, mantida fora do Git; a mesma verificação roda com o arquivo sintético
`inv-R03-strict.xlsx`.

## API

`POST /api/calibracoes/processamento` — `multipart/form-data`, campo `arquivo`.

```bash
curl -i -F arquivo=@src/test/resources/planilhas/ok-aprovado.xlsx \
  http://localhost:8080/api/calibracoes/processamento
```

Resposta (trecho):

```json
{
  "parecer": "APROVADO",
  "pontos": [
    {
      "caracteristica": "Diâmetro eixo",
      "leituras": [4.13, 4.14],
      "valorReferencia": 4.12,
      "media": 4.13500,
      "erroSistematico": 0.01500,
      "lea": 0.03,
      "status": "DENTRO_DA_TOLERANCIA"
    }
  ]
}
```

| Código | Quando |
| ------ | ------ |
| 200 | Planilha processada, com parecer `APROVADO` ou `REPROVADO` (reprovação é resultado, não erro) |
| 400 | Planilha fora do contrato (lista `erros` com linha, coluna, regra e mensagem) ou requisição sem a parte `arquivo` |
| 413 | Arquivo acima de 5 MB |
| 415 | Requisição que não é `multipart/form-data` |
| 500 | Erro inesperado: a resposta traz só um `id`, que liga ao registro no log |

Os erros seguem o formato `application/problem+json` (RFC 9457). Exemplos de todas as
respostas estão em `docs/av03/evidencias/respostas/`.

## Planilhas de exemplo

`src/test/resources/planilhas/` tem 7 planilhas válidas (`ok-*`) e 14 inválidas (`inv-*`,
cada uma violando uma regra do contrato). Os valores esperados de cada uma, calculados de forma
independente do Java, estão em `docs/av03/oraculo-planilhas.md`.

Formato resumido: aba `Medicoes`; colunas `Caracteristica`, `ValorReferencia`, `Leitura1`,
`Leitura2`… (no mínimo duas), `ToleranciaSuperior` e `ToleranciaInferior`; números digitados
como número, sem fórmulas. Detalhes no [contrato](docs/av03/contrato-planilha.md).

As planilhas são geradas por `ferramentas/gerar_planilhas.py` (Python 3 com `openpyxl`;
LibreOffice opcional). A exceção é `inv-R03-strict.xlsx`, salva no Excel desktop, porque o
`openpyxl` não grava o formato Strict.

## Estrutura

```
src/main/java/br/senai_pa3/
  dominio/       cálculo e regras metrológicas (Java puro, sem Spring)
  ingestao/      leitura e validação da planilha (Apache POI)
  aplicacao/     orquestração; SHA-256 e tamanho do arquivo no log
  web/           endpoint REST, DTOs e tratamento de erros
src/main/resources/static/   interface (HTML, CSS e JavaScript, sem bibliotecas)
src/test/                    testes automatizados e planilhas sintéticas
docs/decisoes.md             registro de decisões do projeto
docs/av02/                   relatório da AV02 (arquitetura em nuvem)
docs/av03/                   guia, contrato da planilha, oráculo e evidências de teste
diagramas/                   diagrama de arquitetura da AV02 (.drawio, .png, .svg)
ferramentas/                 gerador das planilhas sintéticas
```

## Dados e LGPD

Todas as planilhas versionadas são sintéticas. Os arquivos operacionais cedidos pela Schulz
(planilhas de cálculo, relatório da MMC e certificado emitido) **não são versionados**: contêm
dados de clientes e dados pessoais de técnicos (nome e matrícula), risco apontado no próprio
diagnóstico da AV01 (RA-11, RA-12, RNF-07).

A aplicação não grava o nome nem o conteúdo do arquivo enviado. O log registra apenas o
SHA-256 e o tamanho, para rastreabilidade.

## Limitações conhecidas (AV03)

- Sem persistência: cada envio é processado e respondido, sem gravação
  ([D-014](docs/decisoes.md)). A persistência entra na AV04.
- Sem autenticação de usuário.
- Não gera o PDF do certificado.
- Não calcula a incerteza de medição.

## Entregas

| #    | Tema                                                    | Data       | Status       |
| ---- | ------------------------------------------------------- | ---------- | ------------ |
| AV01 | Identificação do problema e levantamento de requisitos | 15/09/2026 | Entregue     |
| AV02 | Arquitetura em nuvem                                    | 02/10/2026 | Entregue     |
| AV03 | MVP parte 1 — aplicação transacional                    | 15/10/2026 | Entregue     |
| AV04 | MVP parte 2 — web/mobile e validação                    | 29/10/2026 | —            |
| AV05 | Documentação técnica do MVP e ROI                       | 19/11/2026 | —            |
| AV06 | Pitch técnico-comercial                                 | 03/12/2026 | —            |
