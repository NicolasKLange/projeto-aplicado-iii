#!/usr/bin/env bash
# Empacota as funções e publica o stack do back-end. Credenciais só pelo perfil pa3 (D-022).
# Uso: infra/deploy.sh   (ORIGENS_WEB e URL_VERIFICACAO_BASE são opcionais; ver template.yaml)
# O changeset pede confirmação (precisa de terminal interativo). Sem terminal, por exemplo com "!" no
# Claude Code, use CONFIRMAR_CHANGESET=nao depois de revisar a lista de recursos.
set -euo pipefail
export SAM_CLI_TELEMETRY=0

cd "$(dirname "$0")/.."

PERFIL="${PERFIL:-pa3}"
REGIAO="${REGIAO:-us-east-2}"
STACK="${STACK:-pa3-av04}"
ORIGENS_WEB="${ORIGENS_WEB:-http://localhost:5173}"
# "api" = o QR Code aponta para a própria API, que serve a interface (D-027). Sempre enviado: num
# stack existente, parâmetro omitido mantém o valor anterior.
URL_VERIFICACAO_BASE="${URL_VERIFICACAO_BASE:-api}"
CONFIRMAR_CHANGESET="${CONFIRMAR_CHANGESET:-sim}"

PARAMETROS=("OrigensWeb=${ORIGENS_WEB}" "UrlVerificacaoBase=${URL_VERIFICACAO_BASE}")

if [ "$CONFIRMAR_CHANGESET" = "nao" ]; then
  CONFIRMACAO=--no-confirm-changeset
else
  CONFIRMACAO=--confirm-changeset
fi

# O sam build não resolve o módulo nucleo: o CodeUri aponta para o JAR sombreado.
./mvnw -q -pl funcoes -am package
sam validate --lint --template-file infra/template.yaml --region "$REGIAO"

# --resolve-s3 cria o bucket gerenciado do SAM para o JAR.
sam deploy \
  --template-file infra/template.yaml \
  --stack-name "$STACK" \
  --profile "$PERFIL" \
  --region "$REGIAO" \
  --resolve-s3 \
  --capabilities CAPABILITY_IAM \
  "$CONFIRMACAO" \
  --no-fail-on-empty-changeset \
  --parameter-overrides "${PARAMETROS[@]}"

echo "Back-end publicado. Próximo passo: infra/deploy-web.sh (publica a interface)."
