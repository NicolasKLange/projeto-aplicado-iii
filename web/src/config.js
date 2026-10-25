// Valores públicos vindos de web/.env (infra/gerar-env-web.sh). Faltando algum, falha cedo e alto.
function exigir(nome) {
  const valor = import.meta.env[nome];
  if (!valor) {
    throw new Error(`Configuração ausente: ${nome}. Rode infra/gerar-env-web.sh.`);
  }
  return valor;
}

export const API_URL = exigir('VITE_API_URL');
export const COGNITO_CLIENT_ID = exigir('VITE_COGNITO_CLIENT_ID');
export const AWS_REGION = exigir('VITE_AWS_REGION');
