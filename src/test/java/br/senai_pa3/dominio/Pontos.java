package br.senai_pa3.dominio;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/** Fábrica de {@link PontoEnsaio} para os testes, com os valores escritos como no oráculo. */
final class Pontos {

    private Pontos() {
    }

    /** Tol− = −Tol+ (bilateral simétrica); LEA = Tol+. */
    static PontoEnsaio bilateral(String caracteristica, String referencia, String lea, String... leituras) {
        return ponto(caracteristica, referencia, lea, "-" + lea, leituras);
    }

    /** Tol− = 0 (unilateral); LEA = Tol+. */
    static PontoEnsaio unilateral(String caracteristica, String referencia, String lea, String... leituras) {
        return ponto(caracteristica, referencia, lea, "0", leituras);
    }

    static PontoEnsaio ponto(String caracteristica, String referencia, String tolSup, String tolInf,
            String... leituras) {
        return new PontoEnsaio(caracteristica, new BigDecimal(referencia), new BigDecimal(tolSup),
                new BigDecimal(tolInf), leituras(leituras));
    }

    static List<BigDecimal> leituras(String... valores) {
        return Arrays.stream(valores).map(BigDecimal::new).toList();
    }
}
