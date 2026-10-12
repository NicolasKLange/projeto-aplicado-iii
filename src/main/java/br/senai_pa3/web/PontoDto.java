package br.senai_pa3.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import br.senai_pa3.dominio.PontoEnsaio;
import br.senai_pa3.dominio.ResultadoPonto;

/**
 * Um ponto na resposta da API, com as colunas da tela (T16). Os números vão como número JSON.
 * <p>
 * Média e erro são arredondados a {@value #CASAS} casas só aqui, na saída: o status já veio do
 * domínio em precisão completa ({@code ok-arredondamento}). Cinco casas mostram o 0,00045 desse
 * cenário e cortam as dízimas ({@code ok-tres-leituras}). Leituras, referência e LEA são os
 * valores da planilha, sem arredondamento.
 *
 * @param status nome exato do enum ({@code DENTRO_DA_TOLERANCIA} / {@code FORA_DA_TOLERANCIA})
 */
public record PontoDto(
        String caracteristica,
        List<BigDecimal> leituras,
        BigDecimal valorReferencia,
        BigDecimal media,
        BigDecimal erroSistematico,
        BigDecimal lea,
        String status) {

    static final int CASAS = 5;

    static PontoDto de(ResultadoPonto resultado) {
        PontoEnsaio ponto = resultado.ponto();
        return new PontoDto(
                ponto.caracteristica(),
                ponto.leituras(),
                ponto.valorReferencia(),
                exibicao(resultado.media()),
                exibicao(resultado.erroSistematico()),
                ponto.lea(),
                resultado.status().name());
    }

    /** HALF_EVEN, o mesmo critério da média na calculadora. */
    private static BigDecimal exibicao(BigDecimal valor) {
        return valor.setScale(CASAS, RoundingMode.HALF_EVEN);
    }
}
