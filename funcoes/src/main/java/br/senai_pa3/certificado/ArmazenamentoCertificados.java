package br.senai_pa3.certificado;

/** Porta de gravação do PDF. Em produção é o S3 ({@link S3ArmazenamentoCertificados}). */
public interface ArmazenamentoCertificados {

    /** Grava o PDF em {@code certificados/<id>.pdf}, criptografado em repouso. */
    void gravar(String id, byte[] pdf);
}
