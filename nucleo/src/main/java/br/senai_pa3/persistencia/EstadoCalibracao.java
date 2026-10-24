package br.senai_pa3.persistencia;

/**
 * Estado de um envio de planilha. Cada transição é gravada com condição sobre o
 * estado anterior, o que impede, por exemplo, emitir duas vezes.
 * <ul>
 * <li>{@link #PROCESSANDO}: criado pela {@code fn-api} ao autorizar o upload;
 * ainda não há hash nem resultado.</li>
 * <li>{@link #PROCESSADA}: planilha válida, cálculo gravado ({@code fn-ingestao}).</li>
 * <li>{@link #REJEITADA}: planilha fora do contrato; guarda os erros, nunca
 * dado de negócio ({@code fn-ingestao}).</li>
 * <li>{@link #DUPLICADA}: o hash já pertence a outro envio; aponta para ele
 * ({@code fn-ingestao}).</li>
 * <li>{@link #EMITIDA}: certificado gerado ({@code fn-certificado}).</li>
 * </ul>
 */
public enum EstadoCalibracao {
    PROCESSANDO, PROCESSADA, REJEITADA, DUPLICADA, EMITIDA
}
