#!/usr/bin/env bash
# Teste de ponta a ponta do back-end na nuvem, sem tela (T09). Tudo vem das saídas do stack.
# Uso: infra/smoke.sh        (MANTER_USUARIO=sim preserva o usuário de teste ao final)
# 🔒 As respostas gravadas em docs/av04/evidencias/respostas/ passam por jq: sem JWT, sem URL
# pré-assinada, sem token de verificação e sem número da conta. Credenciais só pelo perfil pa3.
set -euo pipefail

cd "$(dirname "$0")/.."

PERFIL="${PERFIL:-pa3}"
REGIAO="${REGIAO:-us-east-2}"
STACK="${STACK:-pa3-av04}"
EMAIL=smoke-pa3@example.com
NOME="Usuário de Teste"
PLANILHAS=nucleo/src/test/resources/planilhas
SAIDA=docs/av04/evidencias/respostas
MANTER_USUARIO="${MANTER_USUARIO:-nao}"

aws_() { aws --profile "$PERFIL" --region "$REGIAO" "$@"; }
mascarar() { sed -E 's/[0-9]{12}/<conta>/g'; }

saida_stack() {
  aws_ cloudformation describe-stacks --stack-name "$STACK" \
    --query "Stacks[0].Outputs[?OutputKey=='$1'].OutputValue" --output text
}

API=$(saida_stack UrlApi)
POOL=$(saida_stack UserPoolId)
CLIENTE=$(saida_stack UserPoolClientId)
TABELA=$(saida_stack TabelaCalibracoes)
mkdir -p "$SAIDA"
INICIO_MS=$(($(date +%s) * 1000))
FALHAS=0

# Senha aleatória por execução: nunca vai a arquivo, log ou tela.
SENHA="Aa1-$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 20)"

limpar() {
  if [ "$MANTER_USUARIO" != "sim" ]; then
    aws_ cognito-idp admin-delete-user --user-pool-id "$POOL" --username "$EMAIL" >/dev/null 2>&1 || true
  fi
}
trap limpar EXIT

confere() { # descrição, esperado, obtido
  if [ "$2" = "$3" ]; then
    echo "  ok   $1: $3"
  else
    echo "  FALHA $1: esperado '$2', obtido '$3'"
    FALHAS=$((FALHAS + 1))
  fi
}

# --- 1. usuário de teste, com atributo name (a fn-certificado lê pelo sub com AdminGetUser)
echo "== Usuário de teste"
aws_ cognito-idp admin-create-user --user-pool-id "$POOL" --username "$EMAIL" \
  --user-attributes Name=email,Value="$EMAIL" Name=email_verified,Value=true Name=name,Value="$NOME" \
  --message-action SUPPRESS >/dev/null 2>&1 || true
aws_ cognito-idp admin-set-user-password --user-pool-id "$POOL" --username "$EMAIL" \
  --password "$SENHA" --permanent

# --- 2. token. O autorizador JWT confere `aud`, que só o ID token traz.
TOKEN=$(aws_ cognito-idp initiate-auth --auth-flow USER_PASSWORD_AUTH --client-id "$CLIENTE" \
  --auth-parameters "USERNAME=$EMAIL,PASSWORD=$SENHA" --query 'AuthenticationResult.IdToken' --output text)
SUB=$(aws_ cognito-idp admin-get-user --user-pool-id "$POOL" --username "$EMAIL" \
  --query "UserAttributes[?Name=='sub'].Value" --output text)

# curl com cabeçalho de autorização lido de arquivo temporário (o JWT não aparece em ps).
CAB=$(mktemp)
chmod 600 "$CAB"
trap 'rm -f "$CAB"; limpar' EXIT
printf 'Authorization: Bearer %s\n' "$TOKEN" >"$CAB"
api() { curl -sS -H "@$CAB" "$@"; }

# Grava a resposta: status + corpo filtrado por jq. $1 arquivo, $2 status, $3 corpo, $4 filtro jq.
grava() {
  { echo "HTTP $2"; echo "$3" | jq -S "${4:-.}" 2>/dev/null || echo "$3"; } | mascarar >"$SAIDA/$1.txt"
}

# Envia a planilha e espera o estado final. Variáveis de saída: ID, ESTADO, CORPO.
enviar() { # arquivo, rótulo
  local arquivo=$1 rotulo=$2 tamanho resp status corpo url tipo
  tamanho=$(stat -c %s "$arquivo")
  resp=$(api -w '\n%{http_code}' -X POST -H 'Content-Type: application/json' \
    -d "{\"tamanho\": $tamanho}" "$API/uploads")
  status=${resp##*$'\n'}
  corpo=${resp%$'\n'*}
  grava "$rotulo-1-uploads" "$status" "$corpo" 'del(.url)'
  confere "$rotulo POST /uploads" 201 "$status"
  ID=$(echo "$corpo" | jq -r .uploadId)
  url=$(echo "$corpo" | jq -r .url)
  tipo=$(echo "$corpo" | jq -r .contentType)
  status=$(curl -sS -o /dev/null -w '%{http_code}' -X PUT -H "Content-Type: $tipo" \
    --data-binary "@$arquivo" "$url")
  confere "$rotulo PUT no S3" 200 "$status"
  esperar_final
  grava "$rotulo-2-estado" 200 "$CORPO"
}

# Polling de GET /calibracoes/{id} até sair de PROCESSANDO (e de PROCESSADA até EMITIDA).
esperar_final() {
  local i resp
  for i in $(seq 1 40); do
    resp=$(api "$API/calibracoes/$ID")
    CORPO=$resp
    ESTADO=$(echo "$resp" | jq -r '.estado // "?"')
    case "$ESTADO" in PROCESSANDO | PROCESSADA) sleep 2 ;; *) return 0 ;; esac
  done
  return 0
}

# Remove a sentinela do hash para o cenário poder ser repetido (a tabela guarda o hash para sempre).
liberar_hash() {
  local h
  h=$(sha256sum "$1" | cut -d' ' -f1)
  aws_ dynamodb delete-item --table-name "$TABELA" \
    --key "{\"PK\":{\"S\":\"HASH#$h\"},\"SK\":{\"S\":\"UNICO\"}}" >/dev/null
}

OK_REPROVADO=$PLANILHAS/ok-reprovado.xlsx
OK_APROVADO=$PLANILHAS/ok-aprovado.xlsx
INVALIDA=$PLANILHAS/inv-multiplos-erros.xlsx
liberar_hash "$OK_REPROVADO"
liberar_hash "$OK_APROVADO"
liberar_hash "$INVALIDA"

# --- Cenário 1: envio normal até o PDF e a verificação pública
echo "== 1. Envio normal (ok-reprovado.xlsx)"
enviar "$OK_REPROVADO" cenario1
confere "estado final" EMITIDA "$ESTADO"
confere "parecer" REPROVADO "$(echo "$CORPO" | jq -r .parecer)"
confere "status do Diâmetro furo B" FORA_DA_TOLERANCIA \
  "$(echo "$CORPO" | jq -r '[.pontos[] | select(.status=="FORA_DA_TOLERANCIA")] | length | if .==1 then "FORA_DA_TOLERANCIA" else "n=\(.)" end')"
ID1=$ID

resp=$(api -w '\n%{http_code}' "$API/calibracoes/$ID1/pdf")
status=${resp##*$'\n'}
corpo=${resp%$'\n'*}
grava cenario1-3-pdf "$status" "$corpo" 'del(.url)'
confere "GET .../pdf" 200 "$status"
PDF=$SAIDA/certificado-nuvem.pdf
status=$(curl -sS -o "$PDF" -w '%{http_code}' "$(echo "$corpo" | jq -r .url)")
confere "download do PDF" 200 "$status"
confere "cabeçalho %PDF" "%PDF-" "$(head -c 5 "$PDF")"
if command -v pdftotext >/dev/null; then
  confere "nome do Cognito no PDF (AdminGetUser pelo sub)" sim \
    "$(pdftotext "$PDF" - | grep -q "$NOME" && echo sim || echo nao)"
fi

# O token de verificação só existe no item e no QR do PDF; sai do item e não é gravado.
TOKEN_VERIF=$(aws_ dynamodb get-item --table-name "$TABELA" \
  --key "{\"PK\":{\"S\":\"CAL#$ID1\"},\"SK\":{\"S\":\"META\"}}" --query 'Item.token.S' --output text)
resp=$(curl -sS -w '\n%{http_code}' "$API/verificacao/$TOKEN_VERIF")
status=${resp##*$'\n'}
corpo=${resp%$'\n'*}
grava cenario1-4-verificacao "$status" "$corpo"
confere "GET /verificacao/{token} sem token de acesso" 200 "$status"
confere "verificação: sha256Pdf = do arquivo baixado" "$(sha256sum "$PDF" | cut -d' ' -f1)" \
  "$(echo "$corpo" | jq -r .sha256Pdf)"
confere "verificação não expõe sub" 0 "$(echo "$corpo" | grep -c "$SUB" || true)"
status=$(curl -sS -o /dev/null -w '%{http_code}' "$API/verificacao/token-que-nao-existe-0000000000000000000000")
confere "verificação de token inexistente" 404 "$status"

# --- Cenário 2: mesmo arquivo de novo (idempotência por hash)
echo "== 2. Mesmo arquivo, novo envio"
enviar "$OK_REPROVADO" cenario2
confere "estado final" DUPLICADA "$ESTADO"
resp=$(api -w '\n%{http_code}' "$API/calibracoes/$ID/pdf")
confere "PDF da duplicada indisponível" 409 "${resp##*$'\n'}"

# --- Cenário 3: planilha inválida
echo "== 3. Planilha inválida (inv-multiplos-erros.xlsx)"
enviar "$INVALIDA" cenario3
confere "estado final" REJEITADA "$ESTADO"
confere "erros listados" sim "$(echo "$CORPO" | jq -r 'if (.erros | length) > 1 then "sim" else "nao" end')"

# --- Cenário 4: sem token, 401 na borda
echo "== 4. Sem token"
status=$(curl -sS -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
  -d '{"tamanho": 10}' "$API/uploads")
grava cenario4-sem-token "$status" '{}'
confere "POST /uploads sem Authorization" 401 "$status"
status=$(curl -sS -o /dev/null -w '%{http_code}' -H 'Authorization: Bearer lixo' "$API/calibracoes/x")
confere "token inválido" 401 "$status"

# --- Cenário 5: id sem item e tamanho acima do limite
echo "== 5. Bordas da API"
resp=$(api -w '\n%{http_code}' "$API/calibracoes/00000000-0000-4000-8000-000000000000")
status=${resp##*$'\n'}
grava cenario5-id-inexistente "$status" "${resp%$'\n'*}"
confere "id sem item" 404 "$status"
resp=$(api -w '\n%{http_code}' -X POST -H 'Content-Type: application/json' \
  -d '{"tamanho": 5242881}' "$API/uploads")
status=${resp##*$'\n'}
grava cenario5-acima-do-limite "$status" "${resp%$'\n'*}"
confere "tamanho acima de 5 MB" 413 "$status"

# --- Cenário 6: duas invocações simultâneas do mesmo hash (transação do DynamoDB)
echo "== 6. Mesmo hash em dois envios simultâneos (ok-aprovado.xlsx)"
tamanho=$(stat -c %s "$OK_APROVADO")
ids=()
for n in 1 2; do
  corpo=$(api -X POST -H 'Content-Type: application/json' -d "{\"tamanho\": $tamanho}" "$API/uploads")
  ids+=("$(echo "$corpo" | jq -r .uploadId)")
  urls[$n]=$(echo "$corpo" | jq -r .url)
  tipos[$n]=$(echo "$corpo" | jq -r .contentType)
done
for n in 1 2; do
  curl -sS -o /dev/null -X PUT -H "Content-Type: ${tipos[$n]}" --data-binary "@$OK_APROVADO" "${urls[$n]}" &
done
wait
estados=()
for n in 0 1; do
  ID=${ids[$n]}
  esperar_final
  estados+=("$ESTADO")
done
confere "um EMITIDA e um DUPLICADA" "DUPLICADA EMITIDA" "$(printf '%s\n' "${estados[@]}" | sort | paste -sd' ')"
printf '%s\n' "${estados[@]}" | sort | paste -sd' ' >"$SAIDA/cenario6-simultaneos.txt"

# --- Log da fn-ingestao: só id, hash, tamanho e resultado
echo "== Log da fn-ingestao desde o início da execução"
sleep 20
aws_ logs filter-log-events --log-group-name "/aws/lambda/$STACK-fn-ingestao" \
  --start-time "$INICIO_MS" --query 'events[].message' --output json |
  jq -r '.[] | gsub("\n$"; "")' | grep -v -E '^(START|END|REPORT) ' | mascarar >"$SAIDA/log-fn-ingestao.txt" || true
echo "  linhas de log: $(wc -l <"$SAIDA/log-fn-ingestao.txt")"
confere "log sem nome de arquivo (.xlsx)" 0 "$(grep -c -i '\.xlsx\|ok-reprovado\|ok-aprovado\|inv-multiplos' "$SAIDA/log-fn-ingestao.txt" || true)"
confere "log sem sub" 0 "$(grep -c "$SUB" "$SAIDA/log-fn-ingestao.txt" || true)"
confere "log sem JWT" 0 "$(grep -c 'eyJ' "$SAIDA/log-fn-ingestao.txt" || true)"

# --- Varredura final: nada sensível nos arquivos gravados
if grep -r -l -E 'eyJ|X-Amz-|[0-9]{12}' "$SAIDA" --include='*.txt' >/dev/null 2>&1; then
  echo "  FALHA: possível JWT, URL pré-assinada ou número de conta em $SAIDA"
  FALHAS=$((FALHAS + 1))
fi

echo
if [ "$FALHAS" -eq 0 ]; then
  echo "SMOKE: todos os cenários passaram."
else
  echo "SMOKE: $FALHAS verificação(ões) falharam."
  exit 1
fi
