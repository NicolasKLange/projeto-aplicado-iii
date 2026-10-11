package br.senai_pa3.dominio;

import static br.senai_pa3.dominio.Pontos.bilateral;
import static br.senai_pa3.dominio.StatusPonto.DENTRO_DA_TOLERANCIA;
import static br.senai_pa3.dominio.StatusPonto.FORA_DA_TOLERANCIA;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * Classificação por ponto (T10): {@code ResultadoPonto} construído direto com a média do
 * oráculo (docs/av03/oraculo-planilhas.md), sem passar pela calculadora.
 */
class ResultadoPontoTest {

    @Test
    void dentroComFolga() {
        PontoEnsaio ponto = bilateral("Diâmetro eixo", "4.12", "0.03", "4.13", "4.14");

        assertThat(status(ponto, "4.135")).isEqualTo(DENTRO_DA_TOLERANCIA);
    }

    @Test
    void fora() {
        PontoEnsaio ponto = bilateral("Diâmetro furo B", "6.00", "0.03", "6.05", "6.07");

        assertThat(status(ponto, "6.06")).isEqualTo(FORA_DA_TOLERANCIA);
    }

    @Test
    void fronteiraPositiva_erroIgualAoLeaEstaDentro() {
        PontoEnsaio ponto = bilateral("Comprimento pino", "10", "0.08", "10.07", "10.09");

        assertThat(status(ponto, "10.08")).isEqualTo(DENTRO_DA_TOLERANCIA);
    }

    @Test
    void fronteiraNegativa_erroIgualAMenosLeaEstaDentro() {
        PontoEnsaio ponto = bilateral("Diâmetro bucha", "2", "0.05", "1.94", "1.96");

        assertThat(status(ponto, "1.95")).isEqualTo(DENTRO_DA_TOLERANCIA);
    }

    @Test
    void umPassoAcimaDaFronteira_estaFora() {
        // 10⁻¹⁰ é o menor passo da média (escala 10 da calculadora): |E| = 0.0800000001 > LEA.
        PontoEnsaio ponto = bilateral("Comprimento pino", "10", "0.08", "10.07", "10.09");

        assertThat(status(ponto, "10.0800000001")).isEqualTo(FORA_DA_TOLERANCIA);
    }

    @Test
    void erroNegativoDentro() {
        PontoEnsaio ponto = bilateral("Diâmetro alojamento", "20", "0.03", "19.97", "19.98");

        assertThat(status(ponto, "19.975")).isEqualTo(DENTRO_DA_TOLERANCIA);
    }

    @Test
    void arredondamentoPositivo_naoArredondaAntesDeComparar() {
        // |E| = 0.00045 > 0.0004; arredondada a 4 casas (0.0604), a média daria DENTRO.
        PontoEnsaio ponto = bilateral("Diâmetro pino guia", "0.06", "0.0004", "0.0604", "0.0605");

        assertThat(status(ponto, "0.06045")).isEqualTo(FORA_DA_TOLERANCIA);
    }

    @Test
    void arredondamentoNegativo_naoArredondaAntesDeComparar() {
        // E = −0.00045; arredondada a 4 casas (0.0596), a média daria DENTRO.
        PontoEnsaio ponto = bilateral("Diâmetro furo guia", "0.06", "0.0004", "0.0595", "0.0596");

        assertThat(status(ponto, "0.05955")).isEqualTo(FORA_DA_TOLERANCIA);
    }

    // --- apoio ---

    private static StatusPonto status(PontoEnsaio ponto, String media) {
        return new ResultadoPonto(ponto, new BigDecimal(media)).status();
    }
}
