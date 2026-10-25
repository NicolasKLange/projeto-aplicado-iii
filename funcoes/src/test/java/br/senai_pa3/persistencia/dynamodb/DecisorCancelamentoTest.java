package br.senai_pa3.persistencia.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.senai_pa3.persistencia.dynamodb.DecisorCancelamento.Decisao;
import br.senai_pa3.persistencia.dynamodb.DecisorCancelamento.Duplicada;
import br.senai_pa3.persistencia.dynamodb.DecisorCancelamento.Erro;
import br.senai_pa3.persistencia.dynamodb.DecisorCancelamento.Retomada;

class DecisorCancelamentoTest {

    private static final String FALHOU = "ConditionalCheckFailed";

    private static Decisao decidir(String sentinela, String meta, String dono) {
        return DecisorCancelamento.decidir("u-1", List.of(sentinela, meta), Optional.ofNullable(dono));
    }

    @Test
    void sentinelaDoMesmoIdEhRetomada_mesmoComMetaJaAlterado() {
        assertThat(decidir(FALHOU, "None", "u-1")).isEqualTo(new Retomada());
        assertThat(decidir(FALHOU, FALHOU, "u-1")).isEqualTo(new Retomada());
    }

    @Test
    void sentinelaDeOutroIdEhDuplicadaApontandoParaODono() {
        assertThat(decidir(FALHOU, "None", "u-0")).isEqualTo(new Duplicada("u-0"));
    }

    @Test
    void sentinelaQueSumiuEhErroTransitorio() {
        assertThat(decidir(FALHOU, "None", null)).isInstanceOf(Erro.class)
                .extracting(d -> ((Erro) d).transitorio()).isEqualTo(true);
    }

    @Test
    void metaForaDoEstadoEsperadoEhErroDefinitivo() {
        Erro e = (Erro) decidir("None", FALHOU, null);
        assertThat(e.transitorio()).isFalse();
    }

    @Test
    void conflitoELimiteSaoTransitorios() {
        for (String codigo : List.of("TransactionConflict", "ThrottlingError", "ProvisionedThroughputExceeded")) {
            assertThat(((Erro) decidir(codigo, "None", null)).transitorio()).as(codigo).isTrue();
            assertThat(((Erro) decidir("None", codigo, null)).transitorio()).as(codigo).isTrue();
        }
    }

    @Test
    void codigoDesconhecidoOuQuantidadeErradaEhErroDefinitivo() {
        assertThat(decidir("ValidationError", "None", null)).isInstanceOf(Erro.class);
        assertThat(((Erro) decidir("ValidationError", "None", null)).transitorio()).isFalse();
        Decisao umSo = DecisorCancelamento.decidir("u-1", List.of(FALHOU), Optional.of("u-1"));
        assertThat(umSo).isInstanceOf(Erro.class);
    }

    @Test
    void donoDeOutroIdNuncaViraRetomada() {
        assertThat(decidir(FALHOU, FALHOU, "u-0")).isEqualTo(new Duplicada("u-0"));
    }
}
