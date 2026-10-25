package br.senai_pa3.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Presigner real com credenciais fictícias: assina localmente, sem rede. */
class S3UrlsPreassinadasTest {

    private final S3Presigner presigner = S3Presigner.builder().region(Region.US_EAST_2)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIAFICTICIO", "segredo")))
            .build();
    private final S3UrlsPreassinadas urls = new S3UrlsPreassinadas(presigner, "bucket-entrada", "bucket-cert");

    @Test
    void envio_valeCincoMinutosEAssinaContentTypeEContentLength() {
        String url = urls.urlEnvio("sub-1", "id-1", 2048);

        URI uri = URI.create(url);
        String consulta = URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8);
        assertThat(uri.getPath()).isEqualTo("/uploads/sub-1/id-1.xlsx");
        assertThat(uri.getHost()).startsWith("bucket-entrada.s3.us-east-2");
        assertThat(consulta).contains("X-Amz-Expires=300");
        assertThat(consulta).contains("content-length").contains("content-type");
    }

    @Test
    void certificado_valeCincoMinutosParaOObjetoDoId() {
        URI uri = URI.create(urls.urlCertificado("id-1"));

        assertThat(uri.getPath()).isEqualTo("/certificados/id-1.pdf");
        assertThat(uri.getRawQuery()).contains("X-Amz-Expires=300");
    }
}
