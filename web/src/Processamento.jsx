import { useEffect, useRef, useState } from 'react';
import { consultar, enviarArquivo, ErroApi, iniciarEnvio } from './api.js';
import { Erros, Resultado } from './Resultado.jsx';

const LIMITE_BYTES = 5 * 1024 * 1024;
const INTERVALO_MS = 1500;
const LIMITE_MS = 40_000;
// PROCESSADA ainda não tem PDF: a fn-certificado está gerando.
const EM_ANDAMENTO = new Set(['PROCESSANDO', 'PROCESSADA']);

// Fases: ocioso | enviando | processando | resultado | rejeitada | duplicada | demorou | erro.
export default function Processamento({ token, aoExpirar, aoAbrirHistorico }) {
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

      <Mensagem fase={fase} aoConsultar={consultarDeNovo} aoExpirar={aoExpirar}
        aoAbrirHistorico={aoAbrirHistorico} />

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

function Mensagem({ fase, aoConsultar, aoExpirar, aoAbrirHistorico }) {
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
      conteudo = (
        <>
          Esta planilha já foi processada antes; o certificado dela está no histórico.{' '}
          <button type="button" className="secundaria" onClick={aoAbrirHistorico}>Abrir histórico</button>
        </>
      );
      break;
    case 'demorou':
      conteudo = (
        <>
          A planilha ainda está em processamento. Consulte o resultado no histórico em instantes.{' '}
          <button type="button" className="secundaria" onClick={() => aoConsultar(fase.id)}>
            Consultar de novo
          </button>{' '}
          <button type="button" className="secundaria" onClick={aoAbrirHistorico}>Abrir histórico</button>
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
