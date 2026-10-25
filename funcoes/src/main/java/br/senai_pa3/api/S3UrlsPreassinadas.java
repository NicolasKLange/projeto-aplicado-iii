package br.senai_pa3.api;

import java.time.Duration;

import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Adaptador da {@link UrlsPreassinadas} com {@link S3Presigner}. O SDK Java v2 não gera POST pré-assinado,
 * por isso o envio é PUT. A criptografia vem do padrão do bucket (SSE-S3), então não exige cabeçalho.
 */
public class S3UrlsPreassinadas implements UrlsPreassinadas {

    static final Duration VALIDADE = Duration.ofMinutes(5);

    private final S3Presigner presigner;
    private final String bucketEntrada;
    private final String bucketCertificados;

    public S3UrlsPreassinadas(S3Presigner presigner, String bucketEntrada, String bucketCertificados) {
        this.presigner = presigner;
        this.bucketEntrada = bucketEntrada;
        this.bucketCertificados = bucketCertificados;
    }

    @Override
    public String urlEnvio(String sub, String uploadId, long tamanhoBytes) {
        PutObjectRequest pedido = PutObjectRequest.builder()
                .bucket(bucketEntrada)
                .key("uploads/" + sub + "/" + uploadId + ".xlsx")
                .contentType(TIPO_XLSX)
                .contentLength(tamanhoBytes)
                .build();
        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(VALIDADE).putObjectRequest(pedido).build()).url().toString();
    }

    @Override
    public String urlCertificado(String id) {
        GetObjectRequest pedido = GetObjectRequest.builder()
                .bucket(bucketCertificados).key("certificados/" + id + ".pdf").build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(VALIDADE).getObjectRequest(pedido).build()).url().toString();
    }
}
