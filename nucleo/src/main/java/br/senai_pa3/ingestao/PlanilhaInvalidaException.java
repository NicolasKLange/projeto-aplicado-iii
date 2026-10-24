package br.senai_pa3.ingestao;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A planilha não cumpre o contrato. Erro de arquivo ou de estrutura chega sozinho, porque
 * interrompe a leitura; os erros de linha chegam todos juntos.
 */
public class PlanilhaInvalidaException extends RuntimeException {

    private final transient List<ErroValidacao> erros;

    public PlanilhaInvalidaException(List<ErroValidacao> erros) {
        super(mensagem(erros));
        this.erros = List.copyOf(erros);
    }

    /** Pelo menos um erro, na ordem em que foram encontrados. */
    public List<ErroValidacao> erros() {
        return erros;
    }

    private static String mensagem(List<ErroValidacao> erros) {
        Objects.requireNonNull(erros, "erros");
        if (erros.isEmpty()) {
            throw new IllegalArgumentException("Uma planilha inválida precisa de pelo menos um erro.");
        }
        return erros.stream().map(ErroValidacao::mensagem).collect(Collectors.joining(" | "));
    }
}
