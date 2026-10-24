package br.senai_pa3.certificado;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import br.senai_pa3.dominio.CalculadoraMetrologica;
import br.senai_pa3.dominio.PontoEnsaio;
import br.senai_pa3.dominio.ResultadoCalibracao;
import br.senai_pa3.ingestao.LeitorPlanilhaPoi;
import br.senai_pa3.ingestao.Medidor;

/** Só planilhas sintéticas (test-jar do núcleo); nenhum dado real da Schulz. */
class GeradorCertificadoTest {

    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyz012345";
    private static final String URL = "https://exemplo.cloudfront.net/verificar/" + TOKEN;

    private final GeradorCertificado gerador = new GeradorCertificado();

    @Test
    void certificadoDeOkReprovado_trazParecerHashEEnums() throws Exception {
        Planilha p = processar("ok-reprovado.xlsx");

        byte[] pdf = gerador.gerar(dados(p, "Maria da Silva"));
        String texto = textoDe(pdf);

        assertThat(texto)
                .contains("PARECER: REPROVADO")
                .contains(p.sha256())
                .contains("FORA_DA_TOLERANCIA")
                .contains("Gerado por: Maria da Silva")
                .contains(TOKEN)
                .contains("sem validade metrológica");
        assertThat(texto).contains("DENTRO_DA_TOLERANCIA");
        // Instante fixo 2026-10-24T15:00Z = 12:00 em America/Sao_Paulo.
        assertThat(texto).contains("24/10/2026 12:00:00");

        // Evidência para o relatório (§5): aberta e conferida a olho.
        Path destino = Path.of("target", "certificado-ok-reprovado.pdf");
        Files.createDirectories(destino.getParent());
        Files.write(destino, pdf);
    }

    @Test
    void certificadoDeOkAprovado_trazAprovado() throws Exception {
        Planilha p = processar("ok-aprovado.xlsx");

        String texto = textoDe(gerador.gerar(dados(p, "Maria da Silva")));

        assertThat(texto).contains("PARECER: APROVADO").doesNotContain("FORA_DA_TOLERANCIA");
    }

    @Test
    void nomeComCaractereForaDoWinAnsi_naoDerrubaAEmissao() throws Exception {
        Planilha p = processar("ok-aprovado.xlsx");

        String texto = textoDe(gerador.gerar(dados(p, "Zoë 山田")));

        assertThat(texto).contains("Gerado por: Zoë ??");
    }

    private Planilha processar(String arquivo) throws IOException {
        byte[] conteudo;
        try (InputStream in = getClass().getResourceAsStream("/planilhas/" + arquivo)) {
            conteudo = in.readAllBytes();
        }
        try (Medidor medidor = new Medidor(new ByteArrayInputStream(conteudo))) {
            List<PontoEnsaio> pontos = new LeitorPlanilhaPoi().ler(medidor);
            // Garante que o hash cobre o arquivo inteiro, como na ingestão.
            medidor.readAllBytes();
            return new Planilha(new CalculadoraMetrologica().calcular(pontos), medidor.sha256());
        }
    }

    private static DadosCertificado dados(Planilha p, String geradoPor) {
        return new DadosCertificado("11111111-2222-3333-4444-555555555555",
                Instant.parse("2026-10-24T15:00:00Z"), p.sha256(), geradoPor, p.resultado(), TOKEN, URL);
    }

    private static String textoDe(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private record Planilha(ResultadoCalibracao resultado, String sha256) {
    }
}
