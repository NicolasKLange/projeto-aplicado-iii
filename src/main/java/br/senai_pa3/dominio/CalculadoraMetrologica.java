package br.senai_pa3.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Calcula a média das leituras de cada ponto e monta o
 * {@link ResultadoCalibracao}.
 * Erro sistemático, status e parecer são derivados nos records (T08).
 */
public class CalculadoraMetrologica {

    /**
     * Com entradas de até 4 casas, |E| e LEA, quando diferentes, distam ≥ 0,0001/n:
     * o corte
     * em 10⁻¹⁰ nunca inverte a decisão, e |E| = LEA continua exato.
     */
    private static final int ESCALA_MEDIA = 10;

    /** Média aritmética das leituras, com 10 casas decimais. */
    public BigDecimal media(List<BigDecimal> leituras) {
        if (leituras == null || leituras.isEmpty()) {
            throw new IllegalArgumentException("A média precisa de pelo menos uma leitura.");
        }
        BigDecimal soma = leituras.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        // HALF_EVEN: arredondamento sem viés, o padrão do IEEE 754 e da ABNT NBR 5891.
        return soma.divide(BigDecimal.valueOf(leituras.size()), ESCALA_MEDIA, RoundingMode.HALF_EVEN);
    }

    /** Um {@link ResultadoPonto} por ponto, na ordem recebida. */
    public ResultadoCalibracao calcular(List<PontoEnsaio> pontos) {
        return new ResultadoCalibracao(pontos.stream()
                .map(p -> new ResultadoPonto(p, media(p.leituras())))
                .toList());
    }
}
