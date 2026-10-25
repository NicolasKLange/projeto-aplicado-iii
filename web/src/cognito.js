// Login direto no Cognito, sem SDK (T10): a API HTTP pública aceita o fetch do navegador.
//
// Contrato esperado por App.jsx:
//   entrar(email, senha) -> Promise<string>   // devolve o ID token (JWT)
//   rejeita com Error('credenciais') se o Cognito recusar e Error('rede') se a chamada falhar
//
// 🔒 Não logue, não grave em localStorage/sessionStorage e não coloque a senha em URL.
import { AWS_REGION, COGNITO_CLIENT_ID } from './config.js';

const ENDPOINT = `https://cognito-idp.${AWS_REGION}.amazonaws.com/`;

// Erros do Cognito que significam "usuário ou senha não servem". O resto é falha nossa/da rede.
const ERROS_DE_CREDENCIAL = new Set(['NotAuthorizedException', 'UserNotFoundException']);

export async function entrar(email, senha) {
  let resposta;
  try {
    resposta = await fetch(ENDPOINT, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-amz-json-1.1',
        'X-Amz-Target': 'AWSCognitoIdentityProviderService.InitiateAuth',
      },
      body: JSON.stringify({
        AuthFlow: 'USER_PASSWORD_AUTH',
        ClientId: COGNITO_CLIENT_ID,
        AuthParameters: { USERNAME: email, PASSWORD: senha },
      }),
    });
  } catch {
    throw new Error('rede');
  }

  let corpo;
  try {
    corpo = await resposta.json();
  } catch {
    throw new Error('rede');
  }

  if (!resposta.ok) {
    throw new Error(ERROS_DE_CREDENCIAL.has(corpo?.__type) ? 'credenciais' : 'rede');
  }

  // 200 sem token = desafio (ex.: NEW_PASSWORD_REQUIRED), que a tela não trata.
  const idToken = corpo?.AuthenticationResult?.IdToken;
  if (!idToken) {
    throw new Error('credenciais');
  }
  return idToken;
}
