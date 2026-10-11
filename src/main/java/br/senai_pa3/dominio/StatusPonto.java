package br.senai_pa3.dominio;

/**
 * Classificação de um ponto de ensaio: {@code |erro| ≤ LEA} é {@link #DENTRO_DA_TOLERANCIA}
 * (D-T04-1, a fronteira faz parte da tolerância).
 */
public enum StatusPonto {
    DENTRO_DA_TOLERANCIA,
    FORA_DA_TOLERANCIA
}
