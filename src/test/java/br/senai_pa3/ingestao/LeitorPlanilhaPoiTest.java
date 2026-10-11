package br.senai_pa3.ingestao;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import br.senai_pa3.dominio.CalculadoraMetrologica;
import br.senai_pa3.dominio.Parecer;
import br.senai_pa3.dominio.PontoEnsaio;

/** Caminho feliz do leitor (T12), contra o oráculo (docs/av03/oraculo-planilhas.md). */
class LeitorPlanilhaPoiTest {

    private final LeitorPlanilha leitor = new LeitorPlanilhaPoi();

    @Test
    void okAprovado_primeiroPontoCampoACampo() throws IOException {
        List<PontoEnsaio> pontos = ler("ok-aprovado.xlsx");

        assertThat(pontos).hasSize(3);
        PontoEnsaio ponto = pontos.get(0);
        assertThat(ponto.caracteristica()).isEqualTo("Diâmetro eixo");
        // Na célula, o double; com BigDecimal.valueOf, o mesmo decimal do oráculo.
        assertThat(ponto.valorReferencia()).isEqualByComparingTo("4.12");
        assertThat(ponto.tolSuperior()).isEqualByComparingTo("0.03");
        assertThat(ponto.tolInferior()).isEqualByComparingTo("-0.03");
        assertThat(ponto.leituras()).hasSize(2);
        assertThat(ponto.leituras().get(0)).isEqualByComparingTo("4.13");
        assertThat(ponto.leituras().get(1)).isEqualByComparingTo("4.14");
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "ok-aprovado.xlsx,       APROVADO",
            "ok-reprovado.xlsx,      REPROVADO",
            "ok-limite.xlsx,         APROVADO",
            "ok-erro-negativo.xlsx,  APROVADO",
            "ok-unilateral.xlsx,     REPROVADO",
            "ok-arredondamento.xlsx, REPROVADO",
            "ok-tres-leituras.xlsx,  REPROVADO",
    })
    void pontaAPonta_parecerDoOraculo(String arquivo, Parecer esperado) throws IOException {
        List<PontoEnsaio> pontos = ler(arquivo);

        Parecer parecer = new CalculadoraMetrologica().calcular(pontos).parecer();

        assertThat(parecer).isEqualTo(esperado);
    }

    private List<PontoEnsaio> ler(String arquivo) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/planilhas/" + arquivo)) {
            assertThat(in).as(arquivo + " no classpath").isNotNull();
            return leitor.ler(in);
        }
    }
}
