package br.senai_pa3.persistencia;

import java.util.Objects;

/**
 * Desfecho de gravar o hash de um envio. A duplicata é resultado de negócio, não
 * exceção: o pipeline encerra o evento e informa o usuário.
 */
public sealed interface ResultadoRegistro {

    /** Hash novo: o envio ganhou a sentinela e o novo estado. */
    record Registrada() implements ResultadoRegistro {
    }

    /**
     * A sentinela já pertence a este mesmo id: o evento do S3 foi entregue de novo.
     * Nada é alterado.
     */
    record Retomada() implements ResultadoRegistro {
    }

    /** O hash pertence a outro envio; este ficou {@link EstadoCalibracao#DUPLICADA}. */
    record Duplicada(String idOriginal) implements ResultadoRegistro {
        public Duplicada {
            Objects.requireNonNull(idOriginal, "idOriginal");
        }
    }
}
