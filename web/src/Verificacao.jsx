import { useEffect, useState } from 'react';
import { ErroApi, verificarCertificado } from './api.js';
import { Selo } from './Resultado.jsx';

// Mesmo formato do token gerado pela fn-certificado (32 caracteres alfanuméricos).
const TOKEN = /^[A-Za-z0-9]{32}$/;

const DATA_HORA = new Intl.DateTimeFormat('pt-BR', {
  dateStyle: 'short', timeStyle: 'medium', timeZone: 'America/Sao_Paulo',
});

/** Token da rota /verificar/<token>; vazio em /verificar. */
export function tokenDaRota(caminho) {
  const m = /^\/verificar(?:\/([^/]*))?\/?$/.exec(caminho);
  return m ? decodeURIComponent(m[1] ?? '') : null;
}

/** SHA-256 em hexadecimal, calculado no navegador: o PDF não sai do aparelho. */
async function sha256Hex(arquivo) {
  const digest = await crypto.subtle.digest('SHA-256', await arquivo.arrayBuffer());
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

// Página pública (T13, D-015): aberta pelo QR Code impresso no PDF, sem login. Mostra só o que a
// rota pública devolve (identificador, data, parecer, hash), sem nome de pessoa.
export default function Verificacao({ token }) {
  const [digitado, setDigitado] = useState('');
  const [estado, setEstado] = useState({ tipo: token ? 'carregando' : 'pedir' });

  useEffect(() => {
    if (!token) {
      return undefined;
    }
    // Token malformado e inexistente têm a mesma resposta: não ajuda quem tenta adivinhar.
    if (!TOKEN.test(token)) {
      setEstado({ tipo: 'nao-encontrado' });
      return undefined;
    }
    const controle = new AbortController();
    setEstado({ tipo: 'carregando' });
    verificarCertificado(token, controle.signal)
      .then((dados) => {
        if (!controle.signal.aborted) {
          setEstado(dados ? { tipo: 'encontrado', dados } : { tipo: 'nao-encontrado' });
        }
      })
      .catch((e) => {
        if (!controle.signal.aborted) {
          setEstado({ tipo: 'erro', rede: e instanceof ErroApi && e.tipo === 'rede' });
        }
      });
    return () => controle.abort();
  }, [token]);

  function abrirDigitado(evento) {
    evento.preventDefault();
    const valor = digitado.trim();
    if (valor) {
      window.location.assign(`/verificar/${encodeURIComponent(valor)}`);
    }
  }

  return (
    <main className="verificacao">
      <h1>Verificação de certificado</h1>
      <p className="secundario">
        Confira a autenticidade de um certificado de calibração emitido pela plataforma.
      </p>

      {estado.tipo === 'pedir' && (
        <form className="cartao" onSubmit={abrirDigitado}>
          <label htmlFor="token">Código de verificação impresso no certificado</label>
          <input id="token" autoComplete="off" spellCheck="false" maxLength={64} required value={digitado}
            onChange={(e) => setDigitado(e.target.value)} />
          <button type="submit">Verificar</button>
        </form>
      )}
      {estado.tipo === 'carregando' && <div className="mensagem" aria-live="polite">Consultando o certificado…</div>}
      {estado.tipo === 'nao-encontrado' && (
        <div className="mensagem erro" role="alert">
          Certificado não encontrado. Confira o código ou leia o QR Code de novo. Um documento sem registro
          aqui não foi emitido por esta plataforma.
        </div>
      )}
      {estado.tipo === 'erro' && (
        <div className="mensagem erro" role="alert">
          {estado.rede
            ? 'Não foi possível consultar agora. Verifique a conexão e tente de novo.'
            : 'Erro ao consultar o certificado. Tente de novo em instantes.'}
        </div>
      )}
      {estado.tipo === 'encontrado' && <Certificado dados={estado.dados} />}

      <p className="rodape">
        PoC acadêmica (UniSENAI, Projeto Aplicado III) — sem validade metrológica.{' '}
        <a href="/">Acesso da equipe</a>
      </p>
    </main>
  );
}

function Certificado({ dados }) {
  const emitido = new Date(dados.emitidoEm);
  return (
    <>
      <section className="cartao autentico" aria-labelledby="titulo-autentico">
        <h2 id="titulo-autentico">Certificado registrado</h2>
        <dl className="dados">
          <dt>Identificador</dt>
          <dd><code>{dados.id}</code></dd>
          <dt>Emitido em</dt>
          <dd>{Number.isNaN(emitido.getTime()) ? dados.emitidoEm : DATA_HORA.format(emitido)}</dd>
          <dt>Parecer</dt>
          <dd><Selo valor={dados.parecer} /></dd>
          <dt>SHA-256 do PDF</dt>
          <dd><code className="hash">{dados.sha256Pdf}</code></dd>
        </dl>
        <p className="secundario">
          Confira se o identificador e o parecer são os mesmos do documento. Para provar que o PDF não foi
          alterado depois da emissão, escolha o arquivo abaixo: o hash dele tem de ser igual ao registrado.
        </p>
      </section>
      <ConferirArquivo esperado={dados.sha256Pdf} />
    </>
  );
}

/** Conferência automática: o usuário escolhe o PDF recebido e o hash é calculado no aparelho. */
function ConferirArquivo({ esperado }) {
  const [resultado, setResultado] = useState(null);

  async function escolher(evento) {
    const arquivo = evento.target.files[0];
    setResultado(null);
    if (!arquivo) {
      return;
    }
    try {
      const obtido = await sha256Hex(arquivo);
      setResultado({ igual: obtido === String(esperado).toLowerCase(), obtido });
    } catch {
      setResultado({ falha: true });
    }
  }

  return (
    <section className="cartao" aria-labelledby="titulo-conferir">
      <h2 id="titulo-conferir">Conferir o arquivo PDF</h2>
      <p className="secundario">Escolha o PDF recebido. O hash é calculado neste aparelho: o arquivo não é enviado a nenhum servidor.</p>
      <label htmlFor="pdf" className="visualmente-oculto">Arquivo PDF do certificado</label>
      <input id="pdf" type="file" accept="application/pdf,.pdf" onChange={escolher} />
      {resultado?.falha && (
        <p className="mensagem erro" role="alert">Não foi possível ler o arquivo neste navegador.</p>
      )}
      {resultado && !resultado.falha && (
        <p className={`mensagem ${resultado.igual ? 'sucesso' : 'erro'}`} role="status">
          {resultado.igual
            ? 'Arquivo íntegro: o hash do PDF é igual ao registrado na emissão.'
            : 'Atenção: o hash do arquivo é diferente do registrado. Este PDF não é o documento emitido.'}
          <br />
          <span className="secundario">Hash do arquivo: <code className="hash">{resultado.obtido}</code></span>
        </p>
      )}
    </section>
  );
}
