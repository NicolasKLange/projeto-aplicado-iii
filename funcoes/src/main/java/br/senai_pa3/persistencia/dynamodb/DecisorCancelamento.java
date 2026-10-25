package br.senai_pa3.persistencia.dynamodb;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Função pura que traduz o cancelamento de uma {@code TransactWriteItems} de dois itens
 * (sentinela, {@code META}) em decisão de negócio. Sem AWS: recebe só os códigos dos motivos
 * ({@code CancellationReason.code()}, na ordem dos itens) e o dono da sentinela.
 * <ul>
 * <li>sentinela já existe e é do mesmo {@code id}: evento repetido, {@link Retomada};</li>
 * <li>sentinela já existe e é de outro {@code id}: {@link Duplicada};</li>
 * <li>qualquer outra coisa: {@link Erro}, marcado como transitório quando repetir pode resolver.</li>
 * </ul>
 */
final class DecisorCancelamento {

    static final String CONDICAO_FALHOU = "ConditionalCheckFailed";
    static final String SEM_MOTIVO = "None";

    private static final Set<String> TRANSITORIOS = Set.of(
            "TransactionConflict", "ThrottlingError", "ProvisionedThroughputExceeded");

    private static final int SENTINELA = 0;
    private static final int META = 1;

    sealed interface Decisao {
    }

    record Retomada() implements Decisao {
    }

    record Duplicada(String idDono) implements Decisao {
    }

    /** @param transitorio {@code true} se vale repetir o evento (conflito, limite, sentinela sumiu) */
    record Erro(String motivo, boolean transitorio) implements Decisao {
    }

    private DecisorCancelamento() {
    }

    /**
     * @param id        envio que tentou gravar
     * @param codigos   códigos dos motivos, índice 0 = sentinela, 1 = {@code META}
     * @param donoSentinela {@code calId} da sentinela, quando se conseguiu ler
     */
    static Decisao decidir(String id, List<String> codigos, Optional<String> donoSentinela) {
        if (codigos.size() != 2) {
            return new Erro("Cancelamento com %d motivos; esperados 2.".formatted(codigos.size()), false);
        }
        String sentinela = codigos.get(SENTINELA);
        String meta = codigos.get(META);

        if (CONDICAO_FALHOU.equals(sentinela)) {
            if (donoSentinela.isEmpty()) {
                return new Erro("Sentinela existia e sumiu antes da leitura.", true);
            }
            return donoSentinela.get().equals(id) ? new Retomada() : new Duplicada(donoSentinela.get());
        }
        if (TRANSITORIOS.contains(sentinela) || TRANSITORIOS.contains(meta)) {
            return new Erro("Transação cancelada por conflito ou limite.", true);
        }
        if (SEM_MOTIVO.equals(sentinela) && CONDICAO_FALHOU.equals(meta)) {
            return new Erro("Envio inexistente ou fora do estado esperado.", false);
        }
        return new Erro("Cancelamento inesperado: %s / %s.".formatted(sentinela, meta), false);
    }
}
