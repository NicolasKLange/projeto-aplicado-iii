// A API manda os valores metrológicos como texto ("6.0150000000"): nada aqui passa por double.

const CASAS_EXIBICAO = 5;
const DECIMAL = /^(-?)(\d+)(?:\.(\d+))?$/;

/**
 * Média e erro sistemático com 5 casas e vírgula, arredondando HALF_EVEN como o PDF
 * (GeradorCertificado.exibir). Texto fora do formato aparece como veio.
 */
export function cincoCasas(valor) {
  const partes = DECIMAL.exec(String(valor));
  if (!partes) {
    return String(valor);
  }
  const [, sinal, inteira, fracao = ''] = partes;
  let n = BigInt(inteira + fracao.padEnd(CASAS_EXIBICAO, '0'));
  const excesso = Math.max(0, fracao.length - CASAS_EXIBICAO);
  if (excesso > 0) {
    // Arredonda o módulo; o sinal volta no fim (HALF_EVEN é simétrico).
    const divisor = 10n ** BigInt(excesso);
    const resto = n % divisor;
    n /= divisor;
    if (2n * resto > divisor || (2n * resto === divisor && n % 2n === 1n)) {
      n += 1n;
    }
  }
  const digitos = n.toString().padStart(CASAS_EXIBICAO + 1, '0');
  const corte = digitos.length - CASAS_EXIBICAO;
  // -0,00000 não existe: um erro que arredonda a zero aparece sem sinal.
  const negativo = sinal === '-' && n !== 0n;
  return `${negativo ? '-' : ''}${digitos.slice(0, corte)},${digitos.slice(corte)}`;
}

/** Leituras, referência e LEA mantêm as casas que a planilha trouxe; só troca o separador. */
export function valorPlanilha(valor) {
  return String(valor).replace('.', ',');
}
