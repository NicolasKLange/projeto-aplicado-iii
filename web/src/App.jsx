import { useState } from 'react';
import { entrar } from './cognito.js';
import Processamento from './Processamento.jsx';
import { lerUsuario } from './token.js';

// Rota pública /verificar/<token>: entra na T14. Aqui só reservamos o caminho.
const ROTA_PUBLICA = /^\/verificar\/[^/]+$/;

// Tokens só em memória (estado React): recarregar a página pede login de novo, por decisão (T10).
export default function App() {
  const [token, setToken] = useState(null);

  if (ROTA_PUBLICA.test(window.location.pathname)) {
    return (
      <main>
        <h1>Verificação de certificado</h1>
        <p className="secundario">Disponível em breve.</p>
      </main>
    );
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
    </main>
  );
}

function Painel({ token, aoSair }) {
  const { nome, email } = lerUsuario(token);
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
      <Processamento token={token} aoExpirar={aoSair} />
    </main>
  );
}
