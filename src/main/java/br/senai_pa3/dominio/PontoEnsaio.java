package br.senai_pa3.dominio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Uma característica medida, com o valor de referência, a tolerância e as leituras.
 * <p>
 * O construtor repete as regras de linha do contrato da planilha (defesa em profundidade):
 * a ingestão rejeita antes, mas o domínio não aceita um ponto inválido vindo de outra origem.
 *
 * @param caracteristica  identificador do ponto (coluna Caracteristica)
 * @param valorReferencia valor do padrão de referência, em mm
 * @param tolSuperior     Tol+, em mm; maior que zero (R13)
 * @param tolInferior     Tol−, em mm; igual a −Tol+ (bilateral simétrica) ou zero (unilateral) (R14)
 * @param leituras        pelo menos duas leituras, em mm; não negativas se unilateral (R15)
 */
public record PontoEnsaio(
        String caracteristica,
        BigDecimal valorReferencia,
        BigDecimal tolSuperior,
        BigDecimal tolInferior,
        List<BigDecimal> leituras) {

    private static final int MINIMO_LEITURAS = 2;

    public PontoEnsaio {
        Objects.requireNonNull(caracteristica, "caracteristica");
        Objects.requireNonNull(valorReferencia, "valorReferencia");
        Objects.requireNonNull(tolSuperior, "tolSuperior");
        Objects.requireNonNull(tolInferior, "tolInferior");
        Objects.requireNonNull(leituras, "leituras");

        if (caracteristica.isBlank()) {
            throw new IllegalArgumentException("A característica não pode estar em branco.");
        }
        // Cópia imutável: quem criou a lista não altera o ponto depois. Também rejeita null.
        leituras = List.copyOf(leituras);
        if (leituras.size() < MINIMO_LEITURAS) {
            throw new IllegalArgumentException(
                    "'%s': são necessárias pelo menos %d leituras.".formatted(caracteristica, MINIMO_LEITURAS));
        }
        if (tolSuperior.signum() <= 0) {
            throw new IllegalArgumentException(
                    "'%s': a tolerância superior precisa ser maior que zero.".formatted(caracteristica));
        }
        boolean simetrica = tolInferior.compareTo(tolSuperior.negate()) == 0;
        boolean unilateral = tolInferior.signum() == 0;
        if (!simetrica && !unilateral) {
            throw new IllegalArgumentException(
                    "'%s': tolerância +%s/%s fora do escopo do MVP — aceitas: bilateral simétrica ou unilateral."
                            .formatted(caracteristica, tolSuperior.toPlainString(), tolInferior.toPlainString()));
        }
        if (unilateral && leituras.stream().anyMatch(l -> l.signum() < 0)) {
            throw new IllegalArgumentException(
                    "'%s': leitura negativa em característica unilateral.".formatted(caracteristica));
        }
    }

    /** Limite de erro admissível: LEA = Tol+ nos dois tipos de tolerância aceitos (D-T04-2). */
    public BigDecimal lea() {
        return tolSuperior;
    }

    /** {@code true} quando Tol− = 0 (características geométricas: planicidade, posição...). */
    public boolean unilateral() {
        return tolInferior.signum() == 0;
    }
}
