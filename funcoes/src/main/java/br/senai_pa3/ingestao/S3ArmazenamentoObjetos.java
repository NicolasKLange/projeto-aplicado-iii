package br.senai_pa3.ingestao;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

/** Adaptador da {@link ArmazenamentoObjetos} com {@code GetObject} do S3. */
public class S3ArmazenamentoObjetos implements ArmazenamentoObjetos {

    private final S3Client s3;

    /** Cliente padrão: região e credenciais vêm do ambiente do Lambda. */
    public S3ArmazenamentoObjetos() {
        this(S3Client.create());
    }

    public S3ArmazenamentoObjetos(S3Client s3) {
        this.s3 = s3;
    }

    @Override
    public byte[] ler(String bucket, String chave) {
        GetObjectRequest pedido = GetObjectRequest.builder().bucket(bucket).key(chave).build();
        return s3.getObjectAsBytes(pedido).asByteArray();
    }
}
