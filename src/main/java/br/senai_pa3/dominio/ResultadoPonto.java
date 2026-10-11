package br.senai_pa3.dominio;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Resultado do cálculo de um ponto. Carrega o ponto de origem, de onde a tela e
 * o relatório
 * tiram característica, leituras, referência e LEA.
 * <p>
 * Média e erro ficam em precisão completa: o arredondamento é só de exibição,
 * porque
 * arredondar antes de comparar com o LEA muda o status na fronteira
 * ({@code ok-arredondamento}).
 * <p>
 * Erro e status não são campos, são derivados — assim não existe ponto com erro
 * incoerente com a média, nem com erro acima do LEA marcado como dentro. A
 * média continua vindo da calculadora (a divisão exige escolher escala e
 * arredondamento); o erro pode ser derivado porque a subtração é exata.
 *
 * @param ponto ponto de ensaio calculado
 * @param media média aritmética das leituras
 */
public record ResultadoPonto(PontoEnsaio ponto, BigDecimal media) {

    public ResultadoPonto {
        Objects.requireNonNull(ponto, "ponto");
        Objects.requireNonNull(media, "media");
    }

    /** Média − valor de referência (com sinal). */
    public BigDecimal erroSistematico() {
        return media.subtract(ponto.valorReferencia());
    }

    /**
     * {@code |erroSistematico| ≤ LEA} → dentro; a fronteira faz parte da tolerância
     * (D-T04-1).
     */
    public StatusPonto status() {
        return erroSistematico().abs().compareTo(ponto.lea()) <= 0
                ? StatusPonto.DENTRO_DA_TOLERANCIA
                : StatusPonto.FORA_DA_TOLERANCIA;
    }
}
