#!/usr/bin/env bash
# Empacota as funções e publica o stack do back-end. Credenciais só pelo perfil pa3 (D-022).
# Uso: infra/deploy.sh   (ORIGENS_WEB e URL_VERIFICACAO_BASE são opcionais; ver template.yaml)
# O changeset pede confirmação (precisa de terminal interativo). Sem terminal, por exemplo com "!" no
# Claude Code, use CONFIRMAR_CHANGESET=nao depois de revisar a lista de recursos.
set -euo pipefail
export SAM_CLI_TELEMETRY=0

cd "$(dirname "$0")/.."

PERFIL=pa3
REGIAO=us-east-2
STACK=pa3-av04
ORIGENS_WEB="${ORIGENS_WEB:-http://localhost:5173}"
URL_VERIFICACAO_BASE="${URL_VERIFICACAO_BASE:-http://localhost:5173}"
CONFIRMAR_CHANGESET="${CONFIRMAR_CHANGESET:-sim}"

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
  --parameter-overrides "OrigensWeb=${ORIGENS_WEB}" "UrlVerificacaoBase=${URL_VERIFICACAO_BASE}"
