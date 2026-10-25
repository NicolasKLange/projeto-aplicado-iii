package br.senai_pa3.api;

/** Porta das URLs pré-assinadas do S3 (válidas por 5 min). */
public interface UrlsPreassinadas {

    /** Tipo de conteúdo assinado na URL de envio; o cliente precisa mandar exatamente este. */
    String TIPO_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** PUT para {@code uploads/<sub>/<uploadId>.xlsx}, com {@code Content-Type} e {@code Content-Length} assinados. */
    String urlEnvio(String sub, String uploadId, long tamanhoBytes);

    /** GET para {@code certificados/<id>.pdf}. */
    String urlCertificado(String id);
}
