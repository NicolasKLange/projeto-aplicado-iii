// Chamadas à API HTTP e aos buckets por URL pré-assinada (T11).
//
// Toda falha vira ErroApi com um `tipo` que a tela traduz: 'rede' (fetch rejeitou), 'sessao' (401),
// 'problema' (resposta de erro, com o problem+json em `problema` quando houver).
// 🔒 URL pré-assinada e JWT nunca vão para console, estado persistente ou mensagem de erro.
import { API_URL } from './config.js';

export class ErroApi extends Error {
  constructor(tipo, status = 0, problema = null) {
    super(tipo);
    this.tipo = tipo;
    this.status = status;
    this.problema = problema;
  }
}

async function chamar(token, caminho, { signal, ...opcoes } = {}) {
  let resposta;
  try {
    resposta = await fetch(`${API_URL}${caminho}`, {
      ...opcoes,
      signal,
      headers: { ...opcoes.headers, Authorization: `Bearer ${token}` },
    });
  } catch (e) {
    throw e.name === 'AbortError' ? e : new ErroApi('rede');
  }
  if (resposta.status === 401) {
    throw new ErroApi('sessao', 401);
  }
  const corpo = await lerJson(resposta);
  if (!resposta.ok) {
    throw new ErroApi('problema', resposta.status, corpo);
  }
  if (!corpo) {
    throw new ErroApi('problema', resposta.status);
  }
  return corpo;
}

/** Só lê JSON se o servidor disse que é JSON: um proxy no caminho pode devolver HTML. */
async function lerJson(resposta) {
  const tipo = resposta.headers.get('content-type') ?? '';
  if (!tipo.includes('json')) {
    return null;
  }
  try {
    return await resposta.json();
  } catch {
    return null;
  }
}

/** POST /uploads → { uploadId, url, metodo, contentType, expiraEmSegundos }. */
export function iniciarEnvio(token, tamanho, signal) {
  return chamar(token, '/uploads', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ tamanho }),
    signal,
  });
}

/** PUT direto no bucket de entrada. Sem Authorization: a assinatura está na URL. */
export async function enviarArquivo(envio, arquivo, signal) {
  let resposta;
  try {
    resposta = await fetch(envio.url, {
      method: envio.metodo,
      headers: { 'Content-Type': envio.contentType },
      body: arquivo,
      signal,
    });
  } catch (e) {
    throw e.name === 'AbortError' ? e : new ErroApi('rede');
  }
  if (!resposta.ok) {
    // O S3 responde em XML; o corpo não interessa à tela (e pode ecoar a assinatura).
    throw new ErroApi('problema', resposta.status);
  }
}

/** GET /calibracoes → { itens: [{ id, estado, atualizadoEm, parecer? }] }, os 20 mais recentes. */
export function listar(token, signal) {
  return chamar(token, '/calibracoes', { signal });
}

/** GET /calibracoes/{id} → estado, parecer, pontos, erros… */
export function consultar(token, id, signal) {
  return chamar(token, `/calibracoes/${encodeURIComponent(id)}`, { signal });
}

/**
 * Baixa o PDF como blob e entrega pelo navegador. Navegar a aba até a URL do S3 derrubaria a
 * sessão (o token só existe em memória), e window.open depois de um await cai no bloqueador de
 * pop-up. O CORS GET do bucket de certificados libera a origem da tela.
 */
export async function baixarPdf(token, id) {
  const { url } = await chamar(token, `/calibracoes/${encodeURIComponent(id)}/pdf`);
  let resposta;
  try {
    resposta = await fetch(url);
  } catch {
    throw new ErroApi('rede');
  }
  if (!resposta.ok) {
    throw new ErroApi('problema', resposta.status);
  }
  const endereco = URL.createObjectURL(await resposta.blob());
  try {
    const link = document.createElement('a');
    link.href = endereco;
    link.download = `certificado-${id}.pdf`;
    document.body.append(link);
    link.click();
    link.remove();
  } finally {
    // O clique já entregou o blob ao gerenciador de downloads; adiar a revogação evita corrida.
    setTimeout(() => URL.revokeObjectURL(endereco), 1000);
  }
}
