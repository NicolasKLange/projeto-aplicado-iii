package br.senai_pa3.ingestao;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Calcula o SHA-256 e conta os bytes enquanto o leitor consome a entrada, sem guardar uma segunda
 * cópia do arquivo: a R01 do leitor continua limitando a memória.
 * <p>
 * Não registra nada: quem a usa (Spring em {@code app-local}, evento do S3 em {@code funcoes})
 * decide o que logar. O que se pode logar é {@link #sha256()} e {@link #bytes()}, nunca o conteúdo.
 */
public final class Medidor extends DigestInputStream {

    private long bytes;

    public Medidor(InputStream entrada) {
        super(entrada, sha256Digest());
    }

    @Override
    public int read() throws IOException {
        int lido = super.read();
        if (lido != -1) {
            bytes++;
        }
        return lido;
    }

    @Override
    public int read(byte[] destino, int inicio, int tamanho) throws IOException {
        int lidos = super.read(destino, inicio, tamanho);
        if (lidos > 0) {
            bytes += lidos;
        }
        return lidos;
    }

    /**
     * Consome o que o leitor deixou (a R01 para no 5 MB + 1), para o hash ser o do arquivo
     * inteiro. Laço com read, não skip: o skip vai direto à entrada e pula o digest.
     */
    public void consumirRestante() throws IOException {
        byte[] descarte = new byte[8192];
        while (read(descarte) != -1) {
            // só passa pelo digest
        }
    }

    /** SHA-256 em hexadecimal do que passou por aqui; só é o do arquivo após {@link #consumirRestante()}. */
    public String sha256() {
        return HexFormat.of().formatHex(getMessageDigest().digest());
    }

    /** Bytes lidos até agora. */
    public long bytes() {
        return bytes;
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Toda JVM é obrigada a ter SHA-256.
            throw new IllegalStateException(e);
        }
    }
}
