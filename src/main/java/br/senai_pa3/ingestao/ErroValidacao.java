package br.senai_pa3.ingestao;

import java.util.Objects;

/**
 * Um erro de validação da planilha, com o código da regra do contrato
 * (docs/av03/contrato-planilha.md, §6).
 *
 * @param linha    linha no padrão do Excel (cabeçalho = 1); {@code null} em erro de arquivo ou de estrutura
 * @param coluna   cabeçalho da coluna; {@code null} quando o erro não é de uma célula
 * @param regra    código da regra ("R10"): deixa o teste exato e serve para a tela
 * @param mensagem texto do contrato para o operador
 */
public record ErroValidacao(Integer linha, String coluna, String regra, String mensagem) {

    public ErroValidacao {
        Objects.requireNonNull(regra, "regra");
        Objects.requireNonNull(mensagem, "mensagem");
    }

    /** Erro de arquivo ou de estrutura: sem linha nem coluna. */
    public static ErroValidacao geral(String regra, String mensagem) {
        return new ErroValidacao(null, null, regra, mensagem);
    }
}
