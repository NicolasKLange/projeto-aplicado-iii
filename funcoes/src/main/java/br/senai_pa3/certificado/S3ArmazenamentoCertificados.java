package br.senai_pa3.certificado;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/** Adaptador da {@link ArmazenamentoCertificados} com {@code PutObject} e SSE-S3. */
public class S3ArmazenamentoCertificados implements ArmazenamentoCertificados {

    private final S3Client s3;
    private final String bucket;

    public S3ArmazenamentoCertificados(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void gravar(String id, byte[] pdf) {
        s3.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key("certificados/" + id + ".pdf")
                .contentType("application/pdf")
                .serverSideEncryption(ServerSideEncryption.AES256)
                .build(), RequestBody.fromBytes(pdf));
    }
}
