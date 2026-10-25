package br.senai_pa3.ingestao;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FnIngestaoAmbienteTest {

    @Test
    void semVariavelDaTabelaFalhaNaInicializacaoComMensagemClara() {
        assertThatThrownBy(() -> FnIngestao.repositorioReal(nome -> null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TABELA_CALIBRACOES");
    }

    @Test
    void variavelEmBrancoTambemFalha() {
        assertThatThrownBy(() -> FnIngestao.repositorioReal(nome -> "  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TABELA_CALIBRACOES");
    }
}
