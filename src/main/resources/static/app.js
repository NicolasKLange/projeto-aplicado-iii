'use strict';

const ROTA = '/api/calibracoes/processamento';

/** Enum exato da API → rótulo amigável e classe de cor. A cor só reforça o texto. */
const ROTULOS = {
  APROVADO: { rotulo: 'Calibração aprovada', classe: 'ok' },
  REPROVADO: { rotulo: 'Calibração reprovada', classe: 'falha' },
  DENTRO_DA_TOLERANCIA: { rotulo: 'Dentro da tolerância', classe: 'ok' },
  FORA_DA_TOLERANCIA: { rotulo: 'Fora da tolerância', classe: 'falha' },
};

/**
 * Média e erro: o DTO manda 5 casas, mas o JSON.parse descarta os zeros à direita (4.13500 → 4.135).
 * Um formatador só, criado uma vez, devolve as 5 casas com vírgula.
 */
const CINCO_CASAS = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 5, maximumFractionDigits: 5 });

const form = document.getElementById('form-planilha');
const campoArquivo = document.getElementById('arquivo');
const botao = document.getElementById('botao-enviar');
const mensagem = document.getElementById('mensagem');
const secaoParecer = document.getElementById('secao-parecer');
const parecerEnum = document.getElementById('parecer-enum');
const parecerRotulo = document.getElementById('parecer-rotulo');
const secaoTabela = document.getElementById('secao-tabela');
const corpoPontos = document.querySelector('#tabela-pontos tbody');
const secaoErros = document.getElementById('secao-erros');
const corpoErros = document.querySelector('#tabela-erros tbody');

form.addEventListener('submit', processar);
// Trocar de arquivo sem processar não pode deixar o parecer do anterior abaixo do nome novo.
campoArquivo.addEventListener('change', limparTela);

async function processar(evento) {
  evento.preventDefault();
  // Antes de tudo: um parecer antigo visível ao lado de um erro novo é o pior defeito desta tela.
  limparTela();

  // Sem Content-Type: o navegador gera o cabeçalho multipart com o boundary.
  const dados = new FormData();
  dados.append('arquivo', campoArquivo.files[0]);

  // O campo também trava: trocar de arquivo durante o envio poria a resposta do anterior sob o nome novo.
  botao.disabled = true;
  campoArquivo.disabled = true;
  definirMensagem(['Processando…']);

  try {
    // O fetch só rejeita em falha de rede; 400, 413 e 500 chegam aqui com ok === false.
    const resposta = await fetch(ROTA, { method: 'POST', body: dados });
    if (resposta.ok) {
      mostrarResultado(await resposta.json());
    } else {
      await mostrarErro(resposta);
    }
  } catch (erro) {
    console.error(erro);
    // Limpa de novo: uma falha no meio da montagem não pode deixar resultado parcial na tela.
    limparTela();
    definirMensagem([erro instanceof SyntaxError
      ? 'O servidor devolveu uma resposta inesperada. Tente de novo; se persistir, procure o suporte.'
      : 'Não foi possível falar com o servidor. Verifique se ele está no ar e tente de novo.'], true);
  } finally {
    botao.disabled = false;
    campoArquivo.disabled = false;
  }
}

function limparTela() {
  definirMensagem([]);
  secaoParecer.hidden = true;
  secaoParecer.classList.remove('ok', 'falha');
  parecerEnum.textContent = '';
  parecerRotulo.textContent = '';
  secaoTabela.hidden = true;
  corpoPontos.replaceChildren();
  secaoErros.hidden = true;
  corpoErros.replaceChildren();
}

/** Partes são texto ou nós; tudo entra como texto, nunca como HTML. */
function definirMensagem(partes, ehErro = false) {
  mensagem.replaceChildren(...partes);
  mensagem.classList.toggle('erro', ehErro);
}

// ---- 200 ----

function mostrarResultado(resultado) {
  // Monta todas as linhas antes de mostrar qualquer coisa: se algo falhar, nada aparece pela metade.
  const linhas = document.createDocumentFragment();
  resultado.pontos.forEach((ponto, indice) => linhas.append(linhaPonto(ponto, indice + 1)));
  corpoPontos.replaceChildren(linhas);

  const parecer = rotuloDe(resultado.parecer);
  parecerEnum.textContent = resultado.parecer;
  parecerRotulo.textContent = parecer.rotulo;
  if (parecer.classe) {
    secaoParecer.classList.add(parecer.classe);
  }

  secaoParecer.hidden = false;
  secaoTabela.hidden = false;
  const qtd = resultado.pontos.length;
  definirMensagem([`Processamento concluído: ${qtd} ${qtd === 1 ? 'ponto' : 'pontos'}, parecer ${resultado.parecer}.`]);
}

function linhaPonto(ponto, numero) {
  const tr = document.createElement('tr');
  tr.append(
    celula(String(numero)),
    // Característica é texto livre da planilha: textContent, nunca innerHTML.
    celula(ponto.caracteristica),
    // Ponto e vírgula separa as leituras, porque a vírgula já é o separador decimal.
    celula(ponto.leituras.map(valorPlanilha).join('; ')),
    celula(CINCO_CASAS.format(ponto.media), 'num'),
    celula(valorPlanilha(ponto.valorReferencia), 'num'),
    celula(CINCO_CASAS.format(ponto.erroSistematico), 'num'),
    celula(valorPlanilha(ponto.lea), 'num'),
    celulaStatus(ponto.status),
  );
  return tr;
}

function celula(texto, classe) {
  const td = document.createElement('td');
  td.textContent = texto;
  if (classe) {
    td.className = classe;
  }
  return td;
}

function celulaStatus(status) {
  const { rotulo, classe } = rotuloDe(status);
  const selo = document.createElement('span');
  selo.className = classe ? `status ${classe}` : 'status';
  const nome = document.createElement('span');
  nome.className = 'enum';
  nome.textContent = status;
  const amigavel = document.createElement('span');
  amigavel.className = 'rotulo';
  amigavel.textContent = rotulo;
  selo.append(nome, amigavel);

  const td = document.createElement('td');
  td.append(selo);
  return td;
}

/** Enum desconhecido aparece cru e sem cor: melhor neutro do que verde por engano. */
function rotuloDe(valorEnum) {
  return ROTULOS[valorEnum] ?? { rotulo: '', classe: '' };
}

/**
 * Leituras, referência e LEA vêm da planilha sem arredondamento: mantém as casas que o número
 * trouxe (String dá a menor representação que volta ao mesmo double) e só troca para vírgula.
 */
function valorPlanilha(valor) {
  const [mantissa, expoente = '0'] = String(valor).split('e');
  const casasMantissa = (mantissa.split('.')[1] ?? '').length;
  const casas = Math.min(20, Math.max(0, casasMantissa - Number(expoente)));
  return valor.toLocaleString('pt-BR', { minimumFractionDigits: casas, maximumFractionDigits: casas });
}

// ---- !ok ----

async function mostrarErro(resposta) {
  const problema = await lerProblema(resposta);
  if (!problema) {
    definirMensagem([`O servidor respondeu com erro HTTP ${resposta.status}. `
      + 'Tente de novo; se persistir, procure o suporte.'], true);
    return;
  }

  const partes = [problema.detail || `Erro HTTP ${resposta.status} ao processar a planilha.`];
  if (resposta.status === 500 && problema.id) {
    const codigo = document.createElement('code');
    codigo.textContent = problema.id;
    partes.push(' Código: ', codigo, ' (informe este código ao suporte).');
  }
  definirMensagem(partes, true);

  if (Array.isArray(problema.erros) && problema.erros.length > 0) {
    const linhas = document.createDocumentFragment();
    problema.erros.forEach((erro) => linhas.append(linhaErro(erro)));
    corpoErros.replaceChildren(linhas);
    secaoErros.hidden = false;
  }
}

/** Só lê JSON se o servidor disse que é JSON: um proxy no caminho pode devolver HTML. */
async function lerProblema(resposta) {
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

function linhaErro(erro) {
  const tr = document.createElement('tr');
  tr.append(
    // Linha nula = erro de arquivo ou de estrutura, não de uma célula.
    celula(erro.linha == null ? 'Geral' : String(erro.linha)),
    celula(erro.coluna ?? '—'),
    celula(erro.regra ?? '—'),
    celula(erro.mensagem),
  );
  return tr;
}
