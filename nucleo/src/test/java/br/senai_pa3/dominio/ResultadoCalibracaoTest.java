package br.senai_pa3.dominio;

import static br.senai_pa3.dominio.Pontos.bilateral;
import static br.senai_pa3.dominio.Pontos.unilateral;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Parecer final (T10): {@code ResultadoCalibracao} montado com {@code ResultadoPonto} na média
 * do oráculo (docs/av03/oraculo-planilhas.md), sem passar pela calculadora.
 */
class ResultadoCalibracaoTest {

    @Test
    void todosDentro_aprovado() {
        // ok-aprovado
        ResultadoCalibracao resultado = new ResultadoCalibracao(List.of(
                resultado(bilateral("Diâmetro eixo", "4.12", "0.03", "4.13", "4.14"), "4.135"),
                resultado(bilateral("Comprimento eixo", "10.8", "0.02", "10.807", "10.811"), "10.809"),
                resultado(bilateral("Largura base", "0.9288", "0.007", "0.9286", "0.9290"), "0.9288")));

        assertThat(resultado.parecer()).isEqualTo(Parecer.APROVADO);
    }

    @Test
    void umForaEntreVarios_reprovado() {
        // ok-reprovado: só o Diâmetro furo B está fora
        ResultadoCalibracao resultado = new ResultadoCalibracao(List.of(
                resultado(bilateral("Diâmetro furo A", "6.00", "0.03", "6.01", "6.02"), "6.015"),
                resultado(bilateral("Diâmetro furo B", "6.00", "0.03", "6.05", "6.07"), "6.06"),
                resultado(bilateral("Altura flange", "25.4", "0.05", "25.39", "25.41"), "25.4"),
                resultado(unilateral("Planicidade face", "0", "0.05", "0.012", "0.014"), "0.013")));

        assertThat(resultado.parecer()).isEqualTo(Parecer.REPROVADO);
    }

    @Test
    void listaVazia_lancaIllegalArgument() {
        // Stream.allMatch sobre lista vazia devolve true: sem a checagem, seria APROVADO.
        assertThatThrownBy(() -> new ResultadoCalibracao(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- apoio ---

    private static ResultadoPonto resultado(PontoEnsaio ponto, String media) {
        return new ResultadoPonto(ponto, new BigDecimal(media));
    }
}
