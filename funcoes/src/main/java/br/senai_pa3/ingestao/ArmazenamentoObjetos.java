package br.senai_pa3.ingestao;

/**
 * Porta de leitura de objetos. Em produção é o S3 ({@link S3ArmazenamentoObjetos}); no teste,
 * um {@code Map} na memória.
 */
public interface ArmazenamentoObjetos {

    /** Conteúdo do objeto {@code chave} no {@code bucket}. */
    byte[] ler(String bucket, String chave);
}
