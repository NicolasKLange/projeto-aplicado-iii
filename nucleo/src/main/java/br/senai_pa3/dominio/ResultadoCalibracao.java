package br.senai_pa3.dominio;

import java.util.List;
import java.util.Objects;

/**
 * Resultado de uma calibração: os pontos calculados, na ordem da planilha. O
 * parecer final não é campo, é derivado dos pontos — assim não existe
 * calibração com ponto fora e parecer {@link Parecer#APROVADO}.
 * <p>
 * Uma calibração sem pontos é rejeitada: ausência de evidência não pode virar
 * aprovação
 * ({@code Stream.allMatch} sobre lista vazia devolve {@code true}).
 *
 * @param pontos resultados por ponto; pelo menos um
 */
public record ResultadoCalibracao(List<ResultadoPonto> pontos) {

    public ResultadoCalibracao {
        Objects.requireNonNull(pontos, "pontos");
        pontos = List.copyOf(pontos);
        if (pontos.isEmpty()) {
            throw new IllegalArgumentException("A calibração precisa de pelo menos um ponto.");
        }
    }

    /**
     * {@link Parecer#APROVADO} se todos os pontos estão
     * {@link StatusPonto#DENTRO_DA_TOLERANCIA}; senão {@link Parecer#REPROVADO}.
     */
    public Parecer parecer() {
        boolean todosDentro = pontos.stream().allMatch(p -> p.status() == StatusPonto.DENTRO_DA_TOLERANCIA);
        return todosDentro ? Parecer.APROVADO : Parecer.REPROVADO;
    }
}
