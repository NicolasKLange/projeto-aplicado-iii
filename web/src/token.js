// Só leitura de exibição. Quem valida o JWT é o autorizador da API; o navegador não decide nada
// com base nestes campos, apenas mostra quem entrou.
export function lerUsuario(jwt) {
  try {
    const b64 = jwt.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
    const dados = JSON.parse(new TextDecoder().decode(bytes));
    return { nome: dados.name ?? null, email: dados.email ?? null };
  } catch {
    return { nome: null, email: null };
  }
}
