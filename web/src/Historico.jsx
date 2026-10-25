import { useEffect, useRef, useState } from 'react';
import { consultar, ErroApi, listar } from './api.js';
import { Erros, Resultado, Selo } from './Resultado.jsx';

// Mesmo fuso do PDF (GeradorCertificado): a API manda UTC.
const DATA_HORA = new Intl.DateTimeFormat('pt-BR', {
  dateStyle: 'short', timeStyle: 'medium', timeZone: 'America/Sao_Paulo',
});

function dataHora(iso) {
  const data = new Date(iso);
  return Number.isNaN(data.getTime()) ? String(iso) : DATA_HORA.format(data);
}

// Histórico (T12): os 20 envios mais recentes do usuário; "Abrir" mostra o resultado do envio.
export default function Historico({ token, aoExpirar }) {
  const [lista, setLista] = useState({ tipo: 'carregando' });
  const [aberto, setAberto] = useState(null);
  // Um controlador por consulta: a resposta atrasada de um envio não pode ocupar a tela de outro.
  const controle = useRef(null);

  function novoControle() {
    controle.current?.abort();
    controle.current = new AbortController();
    return controle.current.signal;
  }

  async function carregar() {
    const signal = novoControle();
    setAberto(null);
    setLista({ tipo: 'carregando' });
    try {
      const { itens } = await listar(token, signal);
      if (!signal.aborted) {
        setLista({ tipo: 'pronta', itens: Array.isArray(itens) ? itens : [] });
      }
    } catch (e) {
      if (!signal.aborted) {
        setLista({ tipo: 'erro', ...erroDe(e) });
      }
    }
  }

  async function abrir(id) {
    const signal = novoControle();
    setAberto({ id, tipo: 'carregando' });
    window.scrollTo(0, 0);
    try {
      const registro = await consultar(token, id, signal);
      if (!signal.aborted) {
        setAberto({ id, tipo: 'pronto', registro });
      }
    } catch (e) {
      if (!signal.aborted) {
        setAberto({ id, tipo: 'erro', ...erroDe(e) });
      }
    }
  }

  useEffect(() => {
    carregar();
    return () => controle.current?.abort();
    // Só na montagem: o token não muda enquanto a tela existe (sair desmonta o painel).
  }, []);

  if (aberto) {
    return <Detalhe aberto={aberto} token={token} aoVoltar={carregar} aoExpirar={aoExpirar} />;
  }

  return (
    <>
      <div className="acoes">
        <button type="button" className="secundaria" onClick={carregar} disabled={lista.tipo === 'carregando'}>
          Atualizar
        </button>
      </div>
      {lista.tipo === 'carregando' && <div className="mensagem" aria-live="polite">Carregando o histórico…</div>}
      {lista.tipo === 'erro' && <Falha falha={lista} aoExpirar={aoExpirar} />}
      {lista.tipo === 'pronta' && lista.itens.length === 0 && (
        <div className="mensagem" aria-live="polite">Nenhum envio ainda. Processe uma planilha para começar.</div>
      )}
      {lista.tipo === 'pronta' && lista.itens.length > 0 && (
        <section className="cartao">
          <div className="rolagem">
            <table>
              <caption>Últimos {lista.itens.length} envios, do mais recente para o mais antigo</caption>
              <thead>
                <tr>
                  <th scope="col">Atualizado em</th>
                  <th scope="col">Estado</th>
                  <th scope="col">Parecer</th>
                  <th scope="col"><span className="visualmente-oculto">Ação</span></th>
                </tr>
              </thead>
              <tbody>
                {lista.itens.map((item) => (
                  <tr key={item.id}>
                    <td className="num-esquerda">{dataHora(item.atualizadoEm)}</td>
                    <td><Selo valor={item.estado} /></td>
                    <td>{item.parecer ? <Selo valor={item.parecer} /> : '—'}</td>
                    <td>
                      <button type="button" className="secundaria" onClick={() => abrir(item.id)}
                        aria-label={`Abrir o envio de ${dataHora(item.atualizadoEm)}`}>
                        Abrir
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </>
  );
}

function Detalhe({ aberto, token, aoVoltar, aoExpirar }) {
  const registro = aberto.registro;
  return (
    <>
      <div className="acoes">
        <button type="button" className="secundaria" onClick={aoVoltar}>Voltar à lista</button>
      </div>
      {aberto.tipo === 'carregando' && <div className="mensagem" aria-live="polite">Carregando o envio…</div>}
      {aberto.tipo === 'erro' && <Falha falha={aberto} aoExpirar={aoExpirar} />}
      {aberto.tipo === 'pronto' && (
        <>
          <section className="cartao" aria-labelledby="titulo-envio">
            <h2 id="titulo-envio">Envio de {dataHora(registro.atualizadoEm)}</h2>
            <p className="identificacao">
              <Selo valor={registro.estado} />
              {/* O id liga o envio ao log e ao PDF; é opaco, não identifica pessoa nem arquivo. */}
              <span>Identificador: <code>{registro.id}</code></span>
            </p>
          </section>
          <ConteudoDoEnvio registro={registro} token={token} />
        </>
      )}
    </>
  );
}

function ConteudoDoEnvio({ registro, token }) {
  switch (registro.estado) {
    case 'EMITIDA':
      return <Resultado registro={registro} token={token} />;
    case 'PROCESSADA':
      return (
        <>
          <div className="mensagem" aria-live="polite">
            O certificado ainda está sendo gerado. Volte à lista e abra o envio de novo em instantes.
          </div>
          <Resultado registro={registro} token={token} emitido={false} />
        </>
      );
    case 'REJEITADA':
      return <Erros erros={Array.isArray(registro.erros) ? registro.erros : []} />;
    case 'DUPLICADA':
      return (
        <div className="mensagem" aria-live="polite">
          Esta planilha já tinha sido enviada antes. O resultado está no envio anterior da lista.
        </div>
      );
    case 'PROCESSANDO':
      return <div className="mensagem" aria-live="polite">A planilha ainda está em processamento.</div>;
    default:
      return (
        <div className="mensagem erro" aria-live="polite">
          Estado inesperado: {registro.estado}. Procure o suporte.
        </div>
      );
  }
}

function erroDe(e) {
  if (e instanceof ErroApi && e.tipo === 'sessao') {
    return { sessao: true, texto: 'Sua sessão expirou. Entre de novo para continuar.' };
  }
  if (e instanceof ErroApi && e.tipo === 'problema' && e.status === 500 && e.problema?.id) {
    return { texto: 'Erro interno no servidor. Informe este código ao suporte: ', codigo: e.problema.id };
  }
  if (e instanceof ErroApi && e.tipo === 'problema' && e.problema?.detail) {
    return { texto: e.problema.detail };
  }
  if (!(e instanceof ErroApi)) {
    console.error(e);
  }
  return { texto: 'Não foi possível falar com o servidor. Verifique a conexão e tente de novo.' };
}

function Falha({ falha, aoExpirar }) {
  return (
    <div className="mensagem erro" aria-live="polite">
      {falha.texto}
      {falha.codigo && <code>{falha.codigo}</code>}
      {falha.sessao && (
        <>
          {' '}
          <button type="button" className="secundaria" onClick={aoExpirar}>Entrar de novo</button>
        </>
      )}
    </div>
  );
}
