import { useState } from 'react';
import { entrar } from './cognito.js';
import Historico from './Historico.jsx';
import Processamento from './Processamento.jsx';
import { lerUsuario } from './token.js';
import Verificacao, { tokenDaRota } from './Verificacao.jsx';

// Tokens só em memória (estado React): recarregar a página pede login de novo, por decisão (T10).
export default function App() {
  const [token, setToken] = useState(null);

  // Rota pública /verificar[/<token>] (T13): aberta pelo QR Code do PDF, sem login.
  const tokenVerificacao = tokenDaRota(window.location.pathname);
  if (tokenVerificacao !== null) {
    return <Verificacao token={tokenVerificacao} />;
  }
  if (!token) {
    return <Login aoEntrar={setToken} />;
  }
  return <Painel token={token} aoSair={() => setToken(null)} />;
}

function Login({ aoEntrar }) {
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [erro, setErro] = useState('');
  const [ocupado, setOcupado] = useState(false);

  async function enviar(evento) {
    evento.preventDefault();
    setErro('');
    setOcupado(true);
    try {
      aoEntrar(await entrar(email.trim(), senha));
    } catch (e) {
      // Mensagem genérica: não distingue usuário inexistente de senha errada.
      setErro(e.message === 'rede'
        ? 'Não foi possível entrar agora. Tente novamente.'
        : 'Usuário ou senha inválidos.');
      setSenha('');
    } finally {
      setOcupado(false);
    }
  }

  return (
    <main>
      <h1>Certificado de calibração</h1>
      <p className="secundario">Entre com o usuário fornecido pelo administrador.</p>
      <form className="cartao" onSubmit={enviar}>
        <label htmlFor="email">E-mail</label>
        <input id="email" type="email" autoComplete="username" required value={email}
          onChange={(e) => setEmail(e.target.value)} />
        <label htmlFor="senha">Senha</label>
        <input id="senha" type="password" autoComplete="current-password" required value={senha}
          onChange={(e) => setSenha(e.target.value)} />
        <button type="submit" disabled={ocupado}>{ocupado ? 'Entrando…' : 'Entrar'}</button>
      </form>
      <p className="mensagem erro" role="alert" aria-live="polite">{erro}</p>
      <p className="rodape">Recebeu um certificado? <a href="/verificar">Verifique a autenticidade</a> sem login.</p>
    </main>
  );
}

function Painel({ token, aoSair }) {
  const { nome, email } = lerUsuario(token);
  // Duas telas com estado simples (sem roteador, D-019). Trocar de tela desmonta a anterior e
  // cancela o que ela estava consultando.
  const [tela, setTela] = useState('processar');
  return (
    <main>
      <header className="topo">
        <div>
          <h1>Certificado de calibração</h1>
          <p className="secundario">Envie a planilha de ensaio (.xlsx) para calcular os pontos e o parecer.</p>
        </div>
        <div className="usuario">
          <span>Usuário: <strong>{nome ?? email ?? 'autenticado'}</strong></span>
          <button type="button" className="secundaria" onClick={aoSair}>Sair</button>
        </div>
      </header>
      <nav className="abas" aria-label="Telas">
        <button type="button" aria-current={tela === 'processar' ? 'page' : undefined}
          onClick={() => setTela('processar')}>
          Processar planilha
        </button>
        <button type="button" aria-current={tela === 'historico' ? 'page' : undefined}
          onClick={() => setTela('historico')}>
          Histórico
        </button>
      </nav>
      {tela === 'processar'
        ? <Processamento token={token} aoExpirar={aoSair} aoAbrirHistorico={() => setTela('historico')} />
        : <Historico token={token} aoExpirar={aoSair} />}
    </main>
  );
}
