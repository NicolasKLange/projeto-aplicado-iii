package br.senai_pa3.dominio;

/**
 * Parecer final da calibração: {@link #APROVADO} só quando todos os pontos estão
 * {@link StatusPonto#DENTRO_DA_TOLERANCIA}.
 */
public enum Parecer {
    APROVADO,
    REPROVADO
}
