package br.senai_pa3.persistencia.dynamodb;

import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/** Monta o cancelamento de transação como o DynamoDB devolve. */
final class TransactWriteItemsExceptionBuilder {

    private TransactWriteItemsExceptionBuilder() {
    }

    static TransactionCanceledException com(CancellationReason... motivos) {
        return TransactionCanceledException.builder().message("cancelada").cancellationReasons(motivos).build();
    }
}
