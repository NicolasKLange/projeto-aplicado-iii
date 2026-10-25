import { Fragment, useEffect, useRef, useState } from 'react';
import { baixarPdf, consultar, enviarArquivo, ErroApi, iniciarEnvio } from './api.js';
import { cincoCasas, valorPlanilha } from './formato.js';

const LIMITE_BYTES = 5 * 1024 * 1024;
const INTERVALO_MS = 1500;
const LIMITE_MS = 40_000;
// PROCESSADA ainda não tem PDF: a fn-certificado está gerando.
const EM_ANDAMENTO = new Set(['PROCESSANDO', 'PROCESSADA']);

/** Enum exato da API → rótulo amigável e classe de cor. A cor só reforça o texto. */
const ROTULOS = {
  APROVADO: { rotulo: 'Calibração aprovada', classe: 'ok' },
  REPROVADO: { rotulo: 'Calibração reprovada', classe: 'falha' },
  DENTRO_DA_TOLERANCIA: { rotulo: 'Dentro da tolerância', classe: 'ok' },
  FORA_DA_TOLERANCIA: { rotulo: 'Fora da tolerância', classe: 'falha' },
};

/** Enum desconhecido aparece cru e sem cor: melhor neutro do que verde por engano. */
function rotuloDe(valorEnum) {
  return ROTULOS[valorEnum] ?? { rotulo: '', classe: '' };
}

// Fases: ocioso | enviando | processando | resultado | rejeitada | duplicada | demorou | erro.
export default function Processamento({ token, aoExpirar }) {
  const [arquivo, setArquivo] = useState(null);
  const [fase, setFase] = useState({ tipo: 'ocioso' });
  // Um controlador por envio: o polling de uma planilha não pode escrever na tela da seguinte (A12).
  const controle = useRef(null);
  const ocupado = fase.tipo === 'enviando' || fase.tipo === 'processando';

  useEffect(() => () => controle.current?.abort(), []);

  function novoControle() {
    controle.current?.abort();
    controle.current = new AbortController();
    return controle.current.signal;
  }

  function escolher(evento) {
    controle.current?.abort();
    setArquivo(evento.target.files[0] ?? null);
    setFase({ tipo: 'ocioso' });
  }

  async function processar(evento) {
    evento.preventDefault();
    const signal = novoControle();
    const recusa = conferirArquivo(arquivo);
    if (recusa) {
      setFase({ tipo: 'erro', partes: [recusa] });
      return;
    }
    setFase({ tipo: 'enviando' });
    let id = null;
    try {
      const envio = await iniciarEnvio(token, arquivo.size, signal);
      id = envio.uploadId;
      await enviarArquivo(envio, arquivo, signal);
      setFase({ tipo: 'processando', id });
      const registro = await aguardarFinal(token, id, signal);
      if (!signal.aborted) {
        setFase(faseDoRegistro(registro));
      }
    } catch (e) {
      if (!signal.aborted) {
        setFase(faseDoErro(e, id));
      }
    }
  }

  // Depois do limite de espera: mesma consulta, sem reenviar o arquivo.
  async function consultarDeNovo(id) {
    const signal = novoControle();
    setFase({ tipo: 'processando', id });
    try {
      const registro = await aguardarFinal(token, id, signal);
      if (!signal.aborted) {
        setFase(faseDoRegistro(registro));
      }
    } catch (e) {
      if (!signal.aborted) {
        setFase(faseDoErro(e, id));
      }
    }
  }

  return (
    <>
      <form className="cartao" onSubmit={processar}>
        <label htmlFor="arquivo">Planilha de ensaio (.xlsx, até 5 MB)</label>
        <div className="linha-envio">
          {/* O accept só filtra a janela de escolha; quem valida o arquivo é o servidor. */}
          <input type="file" id="arquivo" accept=".xlsx" required disabled={ocupado}
            onChange={escolher} />
          <button type="submit" disabled={ocupado}>
            {ocupado ? 'Processando…' : 'Processar'}
          </button>
        </div>
      </form>

      <Mensagem fase={fase} aoConsultar={consultarDeNovo} aoExpirar={aoExpirar} />

      {fase.tipo === 'resultado' && <Resultado registro={fase.registro} token={token} />}
      {fase.tipo === 'rejeitada' && <Erros erros={fase.erros} />}
    </>
  );
}

/** Conferência local só para poupar a ida à nuvem; o servidor confere de novo. */
function conferirArquivo(arquivo) {
  if (!arquivo) {
    return 'Escolha uma planilha.';
  }
  if (!arquivo.name.toLowerCase().endsWith('.xlsx')) {
    return 'O arquivo precisa ser uma planilha .xlsx.';
  }
  if (arquivo.size === 0) {
    return 'O arquivo está vazio.';
  }
  if (arquivo.size > LIMITE_BYTES) {
    return 'O arquivo excede o limite de 5 MB.';
  }
  return null;
}

/** Consulta a cada 1,5 s até sair de PROCESSANDO/PROCESSADA ou passar de 40 s. */
async function aguardarFinal(token, id, signal) {
  const prazo = Date.now() + LIMITE_MS;
  for (;;) {
    const registro = await consultar(token, id, signal);
    if (!EM_ANDAMENTO.has(registro.estado) || Date.now() + INTERVALO_MS > prazo) {
      return registro;
    }
    await esperar(INTERVALO_MS, signal);
  }
}

function esperar(ms, signal) {
  return new Promise((resolve, reject) => {
    const relogio = setTimeout(resolve, ms);
    signal.addEventListener('abort', () => {
      clearTimeout(relogio);
      reject(signal.reason);
    }, { once: true });
  });
}

function faseDoRegistro(registro) {
  switch (registro.estado) {
    case 'EMITIDA':
      return { tipo: 'resultado', registro };
    case 'REJEITADA':
      return { tipo: 'rejeitada', erros: Array.isArray(registro.erros) ? registro.erros : [] };
    case 'DUPLICADA':
      return { tipo: 'duplicada' };
    case 'PROCESSANDO':
    case 'PROCESSADA':
      return { tipo: 'demorou', id: registro.id };
    default:
      return { tipo: 'erro', partes: [`Estado inesperado: ${registro.estado}. Procure o suporte.`] };
  }
}

function faseDoErro(erro, id) {
  if (!(erro instanceof ErroApi)) {
    console.error(erro);
    return { tipo: 'erro', partes: ['Algo deu errado na tela. Tente de novo; se persistir, procure o suporte.'] };
  }
  if (erro.tipo === 'sessao') {
    return { tipo: 'erro', sessao: true, partes: ['Sua sessão expirou. Entre de novo para continuar.'] };
  }
  if (erro.tipo === 'rede') {
    return {
      tipo: 'erro',
      partes: ['Não foi possível falar com o servidor. Verifique a conexão e tente de novo.'],
    };
  }
  const { status, problema } = erro;
  if (status === 500 && problema?.id) {
    return {
      tipo: 'erro',
      partes: ['Erro interno no servidor. ', 'Código: ', <code key="id">{problema.id}</code>,
        ' (informe este código ao suporte).'],
    };
  }
  if (problema?.detail) {
    return { tipo: 'erro', partes: [problema.detail] };
  }
  // Sem problem+json: em geral, o PUT no S3 (URL expirada ou recusada).
  return {
    tipo: 'erro',
    partes: [id
      ? `O envio do arquivo falhou (HTTP ${status}). Tente de novo.`
      : `O servidor respondeu com erro HTTP ${status}. Tente de novo; se persistir, procure o suporte.`],
  };
}

function Mensagem({ fase, aoConsultar, aoExpirar }) {
  let conteudo = null;
  let erro = false;
  switch (fase.tipo) {
    case 'enviando':
      conteudo = 'Enviando a planilha…';
      break;
    case 'processando':
      conteudo = 'Processando a planilha e gerando o certificado…';
      break;
    case 'resultado': {
      const qtd = fase.registro.pontos?.length ?? 0;
      conteudo = `Processamento concluído: ${qtd} ${qtd === 1 ? 'ponto' : 'pontos'}, `
        + `parecer ${fase.registro.parecer}. Certificado emitido.`;
      break;
    }
    case 'rejeitada':
      conteudo = 'A planilha foi rejeitada. Corrija os itens abaixo e envie de novo.';
      erro = true;
      break;
    case 'duplicada':
      conteudo = 'Esta planilha já foi processada antes; o certificado dela está no histórico.';
      break;
    case 'demorou':
      conteudo = (
        <>
          A planilha ainda está em processamento. Consulte o resultado no histórico em instantes.{' '}
          <button type="button" className="secundaria" onClick={() => aoConsultar(fase.id)}>
            Consultar de novo
          </button>
        </>
      );
      break;
    case 'erro':
      conteudo = (
        <>
          {fase.partes}
          {fase.sessao && (
            <>
              {' '}
              <button type="button" className="secundaria" onClick={aoExpirar}>Entrar de novo</button>
            </>
          )}
        </>
      );
      erro = true;
      break;
    default:
  }
  // A região existe sempre: o leitor de tela só anuncia mudanças numa região já montada.
  return (
    <div className={erro ? 'mensagem erro' : 'mensagem'} aria-live="polite">{conteudo}</div>
  );
}

function Resultado({ registro, token }) {
  const parecer = rotuloDe(registro.parecer);
  return (
    <>
      <section className={`parecer ${parecer.classe}`} aria-labelledby="titulo-parecer">
        <h2 id="titulo-parecer">Parecer final</h2>
        <p className="parecer-valor">
          <span className="enum">{registro.parecer}</span>
          <span className="rotulo">{parecer.rotulo}</span>
        </p>
        <BaixarCertificado key={registro.id} id={registro.id} token={token} />
      </section>

      <section className="cartao">
        <div className="rolagem">
          <table>
            <caption>Resultado por ponto de ensaio</caption>
            <thead>
              <tr>
                <th scope="col">Ponto</th>
                <th scope="col">Característica</th>
                <th scope="col">Leituras</th>
                <th scope="col" className="num">Média</th>
                <th scope="col" className="num">Referência</th>
                <th scope="col" className="num">Erro sistemático</th>
                <th scope="col" className="num">LEA</th>
                <th scope="col">Status</th>
              </tr>
            </thead>
            <tbody>
              {(registro.pontos ?? []).map((ponto, indice) => (
                // A ordem da planilha é a identidade do ponto; a característica pode repetir.
                <tr key={indice}>
                  <td>{indice + 1}</td>
                  <td>{ponto.caracteristica}</td>
                  {/* Ponto e vírgula separa as leituras, porque a vírgula já é o separador decimal. */}
                  <td>{ponto.leituras.map(valorPlanilha).join('; ')}</td>
                  <td className="num">{cincoCasas(ponto.media)}</td>
                  <td className="num">{valorPlanilha(ponto.valorReferencia)}</td>
                  <td className="num">{cincoCasas(ponto.erroSistematico)}</td>
                  <td className="num">{valorPlanilha(ponto.lea)}</td>
                  <td><Status valor={ponto.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}

function Status({ valor }) {
  const { rotulo, classe } = rotuloDe(valor);
  // <wbr> deixa o selo quebrar após "_" em tela estreita sem mudar o texto: o enum continua literal.
  const partes = String(valor).split('_');
  return (
    <span className={classe ? `status ${classe}` : 'status'}>
      <span className="enum">
        {partes.map((parte, i) => (
          <Fragment key={i}>{parte}{i < partes.length - 1 && <>_<wbr /></>}</Fragment>
        ))}
      </span>
      <span className="rotulo">{rotulo}</span>
    </span>
  );
}

function BaixarCertificado({ id, token }) {
  const [baixando, setBaixando] = useState(false);
  const [erro, setErro] = useState('');

  async function baixar() {
    setBaixando(true);
    setErro('');
    try {
      await baixarPdf(token, id);
    } catch (e) {
      setErro(e instanceof ErroApi && e.tipo === 'sessao'
        ? 'Sua sessão expirou. Entre de novo para baixar o certificado.'
        : 'Não foi possível baixar o certificado agora. Tente de novo.');
    } finally {
      setBaixando(false);
    }
  }

  return (
    <div className="baixar">
      <button type="button" onClick={baixar} disabled={baixando}>
        {baixando ? 'Baixando…' : 'Baixar certificado'}
      </button>
      {erro && <p className="erro-baixar" role="alert">{erro}</p>}
    </div>
  );
}

function Erros({ erros }) {
  return (
    <section className="cartao erros" aria-labelledby="titulo-erros">
      <h2 id="titulo-erros">Erros encontrados na planilha</h2>
      <div className="rolagem">
        <table>
          <caption>Corrija cada item e envie a planilha de novo</caption>
          <thead>
            <tr>
              <th scope="col">Linha</th>
              <th scope="col">Coluna</th>
              <th scope="col">Regra</th>
              <th scope="col">Mensagem</th>
            </tr>
          </thead>
          <tbody>
            {erros.map((erro, indice) => (
              <tr key={indice}>
                {/* Linha nula = erro de arquivo ou de estrutura, não de uma célula. */}
                <td>{erro.linha == null ? 'Geral' : erro.linha}</td>
                <td>{erro.coluna ?? '—'}</td>
                <td>{erro.regra ?? '—'}</td>
                <td>{erro.mensagem}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
