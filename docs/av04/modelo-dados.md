# Modelo de dados da AV04 (T04)

Tabela `calibracoes` do DynamoDB (D-007), no padrão de tabela única. Porta no código:
`nucleo/…/persistencia/RepositorioCalibracoes`. Decisões: D-021.

## 1. Itens

| PK | SK | Atributos | Estados | Quem grava |
| --- | --- | --- | --- | --- |
| `CAL#<id>` | `META` | `sub`, `estado`, `atualizadoEm` (UTC, ISO 8601), `GSI2PK`, `GSI2SK` | todos | ver §2 |
| | | `sha256`, `tamanhoBytes` | todos menos `PROCESSANDO` | `fn-ingestao` |
| | | `resultado` (pontos com leituras, média; erro, status e parecer são derivados) | `PROCESSADA`, `EMITIDA` | `fn-ingestao` |
| | | `erros` (lista de linha, coluna, regra, mensagem) | `REJEITADA` | `fn-ingestao` |
| | | `idOriginal` | `DUPLICADA` | `fn-ingestao` |
| | | `token`, `sha256Pdf` | `EMITIDA` | `fn-certificado` |
| `HASH#<sha256>` | `UNICO` | `calId` (dono do hash) | sentinela | `fn-ingestao`, na transação do estado |
| `TOKEN#<token>` | `UNICO` | `calId` | sentinela | `fn-certificado`, na transação da emissão |

Índice **GSI2** (`GSI2PK` = `USER#<sub>`, `GSI2SK` = `atualizadoEm`): lista do usuário (T12).
GSI1 não é criado (D-021).

## 2. Estados e transições

| De | Para | Quem | Condição na gravação |
| --- | --- | --- | --- |
| (nada) | `PROCESSANDO` | `fn-api` (`POST /uploads`) | `attribute_not_exists(PK)` |
| `PROCESSANDO` | `PROCESSADA` | `fn-ingestao` | `estado = PROCESSANDO` **e** sentinela `HASH#` nova, na mesma transação |
| `PROCESSANDO` | `REJEITADA` | `fn-ingestao` | idem (rejeição de negócio) |
| `PROCESSANDO` | `DUPLICADA` | `fn-ingestao` | `estado = PROCESSANDO`; a sentinela pertence a outro `id` |
| `PROCESSADA` | `EMITIDA` | `fn-certificado` | `estado = PROCESSADA` **e** sentinela `TOKEN#` nova, na mesma transação |

Evento repetido (S3 entrega "pelo menos uma vez"): a sentinela já pertence ao **mesmo** `id`, então
é retomada, nada muda. Só uma sentinela de **outro** `id` é duplicata. `EMITIDA` repetida com o mesmo
token também não altera nada.

## 3. Respostas às três perguntas

1. **`uploadId` e id da calibração são o mesmo.** A `fn-api` gera o id antes do envio e grava
   `CAL#<id>` em `PROCESSANDO`; o objeto S3 é `uploads/<sub>/<id>.xlsx`. Enquanto o arquivo é
   processado, a tela faz `GET /calibracoes/{id}` e lê `PROCESSANDO`. Não existe tabela de mapeamento.
2. **Reenvio do mesmo arquivo cria um registro `DUPLICADA`** apontando para o original. O reenvio já
   tem o seu próprio `CAL#<id>` (o id nasce antes do hash), que passa de `PROCESSANDO` para
   `DUPLICADA` com `idOriginal`. O usuário vê "arquivo já enviado" com link para o original.
3. **Planilha rejeitada grava a sentinela.** A versão corrigida tem outro hash e passa. A mesma
   planilha errada reenviada é encerrada antes de abrir o Excel: a `fn-ingestao` calcula o SHA-256
   dos bytes, consulta o dono do hash (`idDoHash`), marca o envio `DUPLICADA` e a tela segue o
   `idOriginal` até os `erros` da rejeição. Só rejeição **de negócio** (`ErroValidacao`) grava a
   sentinela; falha técnica não grava nada, para não bloquear o arquivo por causa de um erro passageiro.
   Limitação: se o contrato da planilha mudar, a mesma planilha continua presa ao hash antigo.

## 4. Comparação com a AV02 (§3.2.2)

| AV02 | AV04 | Motivo |
| --- | --- | --- |
| `ARQ#<sha256>` com nome original, tamanho, chave do objeto | sem item; `sha256` e `tamanhoBytes` ficam no `META` | 🔒 nome do arquivo nunca é gravado; a chave do objeto é derivável de `sub` e `id` |
| `MED#`, `CAR#`, `ORC` | `resultado` embutido no `META` | a PoC tem um único bloco de pontos e não grava orçamento de incerteza (D-018); cabe com folga nos 400 KB do item |
| `CERT#<número>#REV#<n>`, `CERTNUM#`, `SEQ#CERT#` | `token` e `sha256Pdf` no `META`; sem numeração | numeração sequencial e Object Lock estão no roadmap (D-018) |
| Estados `IMPORTADO`, `CALCULADO`, `EM_REVISAO`, `EMITIDO`, `QUARENTENA`, `BLOQUEADO`, `CANCELADO` | `PROCESSANDO`, `PROCESSADA`, `REJEITADA`, `DUPLICADA`, `EMITIDA` | sem revisão nem bloqueio na PoC; `REJEITADA` substitui `QUARENTENA` (o arquivo não vai para `quarentena/`, D-018); `DUPLICADA` e `PROCESSANDO` são novos |
| `INSTR#`, `PADRAO#`, `PROC#`, `CRIT#`, `SOLIC#` | não existem | dados mestres fora do recorte |
| GSI1 (`INSTR#<tag>`), GSI2 (`STATUS#<estado>`) | só GSI2, por `USER#<sub>` | D-021 |
| Sentinelas `HASH#`, `TOKEN#`, `CERTNUM#` | `HASH#`, `TOKEN#` | sem numeração |
| Tabela `auditoria` | fora da T04 (T19, opcional) | |

## 5. 🔒 Privacidade

Nenhum atributo guarda nome do arquivo, conteúdo bruto ou nome do usuário. O dono é o `sub` do
Cognito (opaco). O nome de exibição vai só para o PDF (RN-35). O registro Java recusa combinações
incoerentes (por exemplo `REJEITADA` com resultado) e `sha256` que não tenha 64 dígitos hexadecimais.
