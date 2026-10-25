// Partes da tela de resultado usadas pelo processamento (T11) e pelo histórico (T12).
import { Fragment, useState } from 'react';
import { baixarPdf, ErroApi } from './api.js';
import { cincoCasas, valorPlanilha } from './formato.js';

/** Enum exato da API → rótulo amigável e classe de cor. A cor só reforça o texto. */
const ROTULOS = {
  APROVADO: { rotulo: 'Calibração aprovada', classe: 'ok' },
  REPROVADO: { rotulo: 'Calibração reprovada', classe: 'falha' },
  DENTRO_DA_TOLERANCIA: { rotulo: 'Dentro da tolerância', classe: 'ok' },
  FORA_DA_TOLERANCIA: { rotulo: 'Fora da tolerância', classe: 'falha' },
  PROCESSANDO: { rotulo: 'Em processamento', classe: '' },
  PROCESSADA: { rotulo: 'Gerando o certificado', classe: '' },
  EMITIDA: { rotulo: 'Certificado emitido', classe: '' },
  REJEITADA: { rotulo: 'Planilha rejeitada', classe: 'falha' },
  DUPLICADA: { rotulo: 'Planilha já enviada antes', classe: '' },
};

/** Enum desconhecido aparece cru e sem cor: melhor neutro do que verde por engano. */
export function rotuloDe(valorEnum) {
  return ROTULOS[valorEnum] ?? { rotulo: '', classe: '' };
}

/** Parecer e tabela de pontos; o botão de download só quando o certificado já foi emitido. */
export function Resultado({ registro, token, emitido = true }) {
  const parecer = rotuloDe(registro.parecer);
  return (
    <>
      <section className={`parecer ${parecer.classe}`} aria-labelledby="titulo-parecer">
        <h2 id="titulo-parecer">Parecer final</h2>
        <p className="parecer-valor">
          <span className="enum">{registro.parecer}</span>
          <span className="rotulo">{parecer.rotulo}</span>
        </p>
        {emitido && <BaixarCertificado key={registro.id} id={registro.id} token={token} />}
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
                  <td><Selo valor={ponto.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
}

/** Enum literal + rótulo, com a cor do rótulo quando houver. */
export function Selo({ valor }) {
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
      {rotulo && <span className="rotulo">{rotulo}</span>}
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

export function Erros({ erros }) {
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
