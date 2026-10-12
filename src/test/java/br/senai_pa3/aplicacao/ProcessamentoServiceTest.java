package br.senai_pa3.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import br.senai_pa3.dominio.CalculadoraMetrologica;
import br.senai_pa3.ingestao.LeitorPlanilhaPoi;
import br.senai_pa3.ingestao.PlanilhaInvalidaException;

/**
 * Log do processamento (T15, LGPD): SHA-256 e tamanho do arquivo inteiro, em qualquer desfecho.
 */
@ExtendWith(OutputCaptureExtension.class)
class ProcessamentoServiceTest {

    private final ProcessamentoService servico =
            new ProcessamentoService(new LeitorPlanilhaPoi(), new CalculadoraMetrologica());

    @Test
    void planilhaValida_registraHashETamanho(CapturedOutput saida) throws Exception {
        byte[] conteudo = recurso("ok-aprovado.xlsx");

        servico.processar(new ByteArrayInputStream(conteudo));

        assertThat(saida).contains("sha256=" + sha256(conteudo) + " bytes=" + conteudo.length);
    }

    @Test
    void planilhaInvalida_registraMesmoAssim(CapturedOutput saida) throws Exception {
        byte[] conteudo = recurso("inv-multiplos-erros.xlsx");

        assertThatThrownBy(() -> servico.processar(new ByteArrayInputStream(conteudo)))
                .isInstanceOf(PlanilhaInvalidaException.class);

        assertThat(saida).contains("sha256=" + sha256(conteudo) + " bytes=" + conteudo.length);
    }

    /** A R01 para de ler no 5 MB + 1; o hash ainda tem de ser o do arquivo inteiro. */
    @Test
    void acimaDaR01_hashDoArquivoInteiro(CapturedOutput saida) throws Exception {
        byte[] conteudo = new byte[6 * 1024 * 1024];
        conteudo[conteudo.length - 1] = 7;

        assertThatThrownBy(() -> servico.processar(new ByteArrayInputStream(conteudo)))
                .isInstanceOf(PlanilhaInvalidaException.class);

        assertThat(saida).contains("sha256=" + sha256(conteudo) + " bytes=" + conteudo.length);
    }

    private static byte[] recurso(String arquivo) throws IOException {
        try (InputStream in = ProcessamentoServiceTest.class.getResourceAsStream("/planilhas/" + arquivo)) {
            return in.readAllBytes();
        }
    }

    private static String sha256(byte[] conteudo) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
    }
}
