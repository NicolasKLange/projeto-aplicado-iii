package br.senai_pa3.aplicacao;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.senai_pa3.dominio.CalculadoraMetrologica;
import br.senai_pa3.dominio.PontoEnsaio;
import br.senai_pa3.dominio.ResultadoCalibracao;
import br.senai_pa3.ingestao.LeitorPlanilha;
import br.senai_pa3.ingestao.PlanilhaInvalidaException;

/**
 * Caso de uso do processamento: planilha → pontos → resultado. Não conhece HTTP; recebe o
 * conteúdo como {@link InputStream}, o mesmo que a {@code fn-ingestao} vai entregar.
 * <p>
 * Cada planilha deixa no log o SHA-256 e o tamanho, nunca o conteúdo nem o nome do arquivo (LGPD).
 */
@Service
public class ProcessamentoService {

    private static final Logger log = LoggerFactory.getLogger(ProcessamentoService.class);

    private final LeitorPlanilha leitor;
    private final CalculadoraMetrologica calculadora;

    public ProcessamentoService(LeitorPlanilha leitor, CalculadoraMetrologica calculadora) {
        this.leitor = leitor;
        this.calculadora = calculadora;
    }

    /**
     * Lê e calcula. Quem chama fecha a {@code entrada}.
     *
     * @throws PlanilhaInvalidaException se a planilha não cumpre o contrato (R01–R15)
     */
    public ResultadoCalibracao processar(InputStream entrada) {
        Medidor medidor = new Medidor(entrada);
        try {
            List<PontoEnsaio> pontos = leitor.ler(medidor);
            return calculadora.calcular(pontos);
        } finally {
            medidor.registrar();
        }
    }

    /**
     * Calcula o hash e conta os bytes enquanto o leitor consome a entrada, sem guardar uma segunda
     * cópia do arquivo: a R01 do leitor continua limitando a memória.
     */
    private static final class Medidor extends DigestInputStream {

        private long bytes;

        Medidor(InputStream entrada) {
            super(entrada, sha256());
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
        void registrar() {
            try {
                byte[] descarte = new byte[8192];
                while (read(descarte) != -1) {
                    // só passa pelo digest
                }
                log.info("Planilha recebida: sha256={} bytes={}",
                        HexFormat.of().formatHex(getMessageDigest().digest()), bytes);
            } catch (IOException e) {
                // Não mascara o erro do processamento, que já está subindo.
                log.warn("Planilha recebida: hash indisponível, a entrada falhou após {} bytes", bytes);
            }
        }

        private static MessageDigest sha256() {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                // Toda JVM é obrigada a ter SHA-256.
                throw new IllegalStateException(e);
            }
        }
    }
}
