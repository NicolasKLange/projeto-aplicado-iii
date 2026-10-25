#!/usr/bin/env bash
# Gera web/.env com as saídas do stack (nada fixo no código). Só valores públicos: URL da API,
# id do cliente do Cognito e região. Credenciais só pelo perfil pa3 (D-022).
set -euo pipefail
cd "$(dirname "$0")/.."

PERFIL=pa3
REGIAO=us-east-2
STACK=pa3-av04

saida() {
  aws --profile "$PERFIL" --region "$REGIAO" cloudformation describe-stacks --stack-name "$STACK" \
    --query "Stacks[0].Outputs[?OutputKey=='$1'].OutputValue" --output text
}

cat >web/.env <<EOF
VITE_API_URL=$(saida UrlApi)
VITE_COGNITO_CLIENT_ID=$(saida UserPoolClientId)
VITE_AWS_REGION=$REGIAO
EOF
echo "web/.env gerado."
