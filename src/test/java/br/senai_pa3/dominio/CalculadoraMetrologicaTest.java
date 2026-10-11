package br.senai_pa3.dominio;

import static br.senai_pa3.dominio.Pontos.bilateral;
import static br.senai_pa3.dominio.Pontos.leituras;
import static br.senai_pa3.dominio.Pontos.unilateral;
import static br.senai_pa3.dominio.StatusPonto.DENTRO_DA_TOLERANCIA;
import static br.senai_pa3.dominio.StatusPonto.FORA_DA_TOLERANCIA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Um teste por cenário válido do oráculo (docs/av03/oraculo-planilhas.md, T06), mais os
 * casos de {@code media(...)} direto. Os valores esperados vêm do oráculo, calculado em
 * Python com {@code decimal}, independente desta implementação; nas dízimas, o esperado é
 * o valor exato do Java (escala 10, HALF_EVEN), com o do oráculo em comentário.
 */
class CalculadoraMetrologicaTest {

    private final CalculadoraMetrologica calculadora = new CalculadoraMetrologica();

    // --- media(...) direto ---

    @Test
    void media_tresLeiturasComDivisaoExata() {
        // Três leituras sem dízima: se lançar ArithmeticException, a causa é a dízima, não o n.
        BigDecimal media = calculadora.media(leituras("10.02", "10.04", "10.03"));

        assertThat(media).isEqualByComparingTo("10.03");
    }

    @Test
    void media_listaVaziaLancaIllegalArgument() {
        assertThatThrownBy(() -> calculadora.media(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void media_nullLancaIllegalArgument() {
        assertThatThrownBy(() -> calculadora.media(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- cenários do oráculo ---

    @Test
    void okAprovado_todosDentroComFolga() {
        ResultadoCalibracao resultado = calculadora.calcular(List.of(
                bilateral("Diâmetro eixo", "4.12", "0.03", "4.13", "4.14"),
                bilateral("Comprimento eixo", "10.8", "0.02", "10.807", "10.811"),
                bilateral("Largura base", "0.9288", "0.007", "0.9286", "0.9290")));

        confere(resultado, Parecer.APROVADO,
                esperado("4.135", "0.015", DENTRO_DA_TOLERANCIA),
                esperado("10.809", "0.009", DENTRO_DA_TOLERANCIA),
                esperado("0.9288", "0", DENTRO_DA_TOLERANCIA));
    }

    @Test
    void okReprovado_umForaEntreQuatro() {
        ResultadoCalibracao resultado = calculadora.calcular(List.of(
                bilateral("Diâmetro furo A", "6.00", "0.03", "6.01", "6.02"),
                bilateral("Diâmetro furo B", "6.00", "0.03", "6.05", "6.07"),
                bilateral("Altura flange", "25.4", "0.05", "25.39", "25.41"),
                unilateral("Planicidade face", "0", "0.05", "0.012", "0.014")));

        confere(resultado, Parecer.REPROVADO,
                esperado("6.015", "0.015", DENTRO_DA_TOLERANCIA),
                esperado("6.06", "0.06", FORA_DA_TOLERANCIA),
                esperado("25.4", "0", DENTRO_DA_TOLERANCIA),
                esperado("0.013", "0.013", DENTRO_DA_TOLERANCIA));
    }

    @Test
    void okLimite_erroIgualAoLeaNosDoisLados() {
        ResultadoCalibracao resultado = calculadora.calcular(List.of(
                bilateral("Comprimento pino", "10", "0.08", "10.07", "10.09"),
                bilateral("Diâmetro bucha", "2", "0.05", "1.94", "1.96")));

        confere(resultado, Parecer.APROVADO,
                esperado("10.08", "0.08", DENTRO_DA_TOLERANCIA),
                esperado("1.95", "-0.05", DENTRO_DA_TOLERANCIA));
    }

    @Test
    void okErroNegativo_mediaAbaixoDaReferenciaAindaDentro() {
        ResultadoCalibracao resultado = calculadora.calcular(List.of(
                bilateral("Diâmetro alojamento", "20", "0.03", "19.97", "19.98"),
                bilateral("Espessura chapa", "3.5", "0.02", "3.49", "3.49")));

        confere(resultado, Parecer.APROVADO,
                esperado("19.975", "-0.025", DENTRO_DA_TOLERANCIA),
                esperado("3.49", "-0.01", DENTRO_DA_TOLERANCIA));
    }

    @Test
    void okUnilateral_umDentroUmForaELeituraZero() {
        ResultadoCalibracao resultado = calculadora.calcular(List.of(
                unilateral("Planicidade base", "0", "0.05", "0.02", "0.03"),
                unilateral("Posição furo", "0", "0.1", "0.11", "0.13"),
                unilateral("Paralelismo face", "0", "0.01", "0", "0.004")));

        confere(resultado, Parecer.REPROVADO,
                esperado("0.025", "0.025", DENTRO_DA_TOLERANCIA),
                esperado("0.12", "0.12", FORA_DA_TOLERANCIA),
                esperado("0.002", "0.002", DENTRO_DA_TOLERANCIA));
    }

    @Test
    void okArredondamento_arredondarAntesDeCompararAprovariaIndevidamente() {
        ResultadoCalibracao resultado = calculadora.calcular(List.of(
                bilateral("Diâmetro pino guia", "0.06", "0.0004", "0.0604", "0.0605"),
                bilateral("Diâmetro furo guia", "0.06", "0.0004", "0.0595", "0.0596")));

        confere(resultado, Parecer.REPROVADO,
                esperado("0.06045", "0.00045", FORA_DA_TOLERANCIA),
                esperado("0.05955", "-0.00045", FORA_DA_TOLERANCIA));
    }

    /**
     * Prova a escala e o modo de arredondamento: em 10 casas, HALF_EVEN e HALF_UP dão
     * 10.0166666667 no Comprimento haste; DOWN daria 10.0166666666. Escala diferente de 10
     * também falha, porque a comparação é exata.
     */
    @Test
    void okTresLeituras_dizimaEmEscala10HalfEven() {
        ResultadoCalibracao resultado = calculadora.calcular(List.of(
                bilateral("Comprimento haste", "10", "0.02", "10.01", "10.02", "10.02"),
                bilateral("Diâmetro haste", "1", "0.003", "1.00", "1.00", "1.01")));

        confere(resultado, Parecer.REPROVADO,
                // oráculo: média 10.01666666666666666666666667, erro 0.01666666666666666666666667
                esperado("10.0166666667", "0.0166666667", DENTRO_DA_TOLERANCIA),
                // oráculo: média 1.003333333333333333333333333, erro 0.003333333333333333333333333
                esperado("1.0033333333", "0.0033333333", FORA_DA_TOLERANCIA));
    }

    // --- apoio ---

    private record Esperado(BigDecimal media, BigDecimal erro, StatusPonto status) {
    }

    private static Esperado esperado(String media, String erro, StatusPonto status) {
        return new Esperado(new BigDecimal(media), new BigDecimal(erro), status);
    }

    private static void confere(ResultadoCalibracao resultado, Parecer parecer, Esperado... esperados) {
        assertThat(resultado.pontos()).hasSize(esperados.length);
        for (int i = 0; i < esperados.length; i++) {
            ResultadoPonto ponto = resultado.pontos().get(i);
            String nome = ponto.ponto().caracteristica();
            assertThat(ponto.media()).as(nome + ": média").isEqualByComparingTo(esperados[i].media());
            assertThat(ponto.erroSistematico()).as(nome + ": erro").isEqualByComparingTo(esperados[i].erro());
            assertThat(ponto.status()).as(nome + ": status").isEqualTo(esperados[i].status());
        }
        assertThat(resultado.parecer()).as("parecer").isEqualTo(parecer);
    }
}
