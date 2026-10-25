package br.senai_pa3.certificado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.senai_pa3.dominio.PontoEnsaio;
import br.senai_pa3.dominio.ResultadoCalibracao;
import br.senai_pa3.dominio.ResultadoPonto;
import br.senai_pa3.persistencia.EstadoCalibracao;
import br.senai_pa3.persistencia.RegistroCalibracao;
import br.senai_pa3.persistencia.RepositorioEmMemoria;

/** Sem AWS: o S3 é um Map na memória e o repositório é o adaptador em memória do núcleo. */
class FnCertificadoTest {

    private static final Instant AGORA = Instant.parse("2026-10-25T12:00:00Z");
    private static final String SHA = "a".repeat(64);

    private final RepositorioEmMemoria repositorio = new RepositorioEmMemoria();
    private final Map<String, byte[]> pdfs = new HashMap<>();
    private final List<String> gravacoes = new ArrayList<>();
    private final List<String> subsConsultados = new ArrayList<>();
    private Optional<String> nome = Optional.of("Maria Teste");
    private FnCertificado fn;

    @BeforeEach
    void montar() {
        fn = criar((id, pdf) -> {
            pdfs.put(id, pdf);
            gravacoes.add(id);
        });
    }

    private FnCertificado criar(ArmazenamentoCertificados armazenamento) {
        return new FnCertificado(repositorio, armazenamento, sub -> {
            subsConsultados.add(sub);
            return nome;
        }, new GeradorCertificado(),
                Clock.fixed(AGORA, ZoneOffset.UTC), new SecureRandom(), "https://exemplo.cloudfront.net/");
    }

    private void processada(String id) {
        repositorio.iniciar(id, "sub-1", AGORA);
        PontoEnsaio p = new PontoEnsaio("P1", new BigDecimal("10"), new BigDecimal("0.05"),
                new BigDecimal("-0.05"), List.of(new BigDecimal("10.00"), new BigDecimal("10.02")));
        repositorio.registrarProcessada(id, SHA, 2048L,
                new ResultadoCalibracao(List.of(new ResultadoPonto(p, new BigDecimal("10.0100000000")))), AGORA);
    }

    private static Map<String, Object> envelope(Map<String, Object> responsePayload) {
        return Map.of("version", "1.0", "requestPayload", Map.of("Records", List.of()),
                "responsePayload", responsePayload);
    }

    @Test
    void processada_geraPdfGravaEPassaParaEmitida() {
        processada("u-1");

        Map<String, String> retorno = fn.handleRequest(envelope(Map.of("calibracaoId", "u-1")), null);

        assertThat(retorno).containsExactly(Map.entry("calibracaoId", "u-1"));
        assertThat(new String(pdfs.get("u-1"), 0, 5)).isEqualTo("%PDF-");
        RegistroCalibracao r = repositorio.buscar("u-1").orElseThrow();
        assertThat(r.estado()).isEqualTo(EstadoCalibracao.EMITIDA);
        assertThat(r.token()).matches("[A-Za-z0-9]{32}");
        assertThat(r.sha256Pdf()).matches("[0-9a-f]{64}");
        assertThat(repositorio.buscarPorToken(r.token())).map(RegistroCalibracao::id).contains("u-1");
    }

    @Test
    void nomeVemDoDiretorioPeloSub_eNaoVaiParaATabelaNemParaOLog() throws Exception {
        processada("u-6");
        java.util.List<String> linhas = new ArrayList<>();
        java.util.logging.Handler captura = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord r) {
                linhas.add(new java.util.logging.SimpleFormatter().formatMessage(r));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(FnCertificado.class.getName());
        logger.addHandler(captura);
        try {
            fn.handleRequest(envelope(Map.of("calibracaoId", "u-6")), null);
        } finally {
            logger.removeHandler(captura);
        }

        assertThat(subsConsultados).containsExactly("sub-1");
        try (var doc = org.apache.pdfbox.Loader.loadPDF(pdfs.get("u-6"))) {
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(doc))
                    .contains("Gerado por: Maria Teste");
        }
        assertThat(repositorio.buscar("u-6").toString()).doesNotContain("Maria");
        assertThat(String.join("\n", linhas)).doesNotContain("Maria", "sub-1");
    }

    @Test
    void semNomeNoDiretorio_usaORotuloNeutro() throws Exception {
        processada("u-7");
        nome = Optional.empty();

        fn.handleRequest(envelope(Map.of("calibracaoId", "u-7")), null);

        try (var doc = org.apache.pdfbox.Loader.loadPDF(pdfs.get("u-7"))) {
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(doc))
                    .contains("Gerado por: Usuário autenticado");
        }
    }

    @Test
    void falhaNoDiretorio_sobeParaADlqSemEmitir() {
        processada("u-8");
        FnCertificado quebrado = new FnCertificado(repositorio, (id, pdf) -> gravacoes.add(id), sub -> {
            throw new IllegalStateException("Cognito fora do ar");
        }, new GeradorCertificado(), Clock.fixed(AGORA, ZoneOffset.UTC), new SecureRandom(), "https://x");

        assertThatThrownBy(() -> quebrado.handleRequest(envelope(Map.of("calibracaoId", "u-8")), null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(gravacoes).isEmpty();
        assertThat(repositorio.buscar("u-8").orElseThrow().estado()).isEqualTo(EstadoCalibracao.PROCESSADA);
    }

    @Test
    void envelopeSemCalibracaoId_ehIgnoradoComSucesso() {
        processada("u-2");

        assertThat(fn.handleRequest(envelope(Map.of()), null)).isEmpty();
        assertThat(fn.handleRequest(Map.of(), null)).isEmpty();
        assertThat(fn.handleRequest(null, null)).isEmpty();

        assertThat(gravacoes).isEmpty();
        assertThat(repositorio.buscar("u-2").orElseThrow().estado()).isEqualTo(EstadoCalibracao.PROCESSADA);
    }

    @Test
    void reentregaDeEmitida_naoGeraNemGravaDeNovo() {
        processada("u-3");
        fn.handleRequest(envelope(Map.of("calibracaoId", "u-3")), null);
        RegistroCalibracao antes = repositorio.buscar("u-3").orElseThrow();

        fn.handleRequest(envelope(Map.of("calibracaoId", "u-3")), null);

        assertThat(gravacoes).hasSize(1);
        assertThat(repositorio.buscar("u-3")).contains(antes);
    }

    @Test
    void estadoQueNaoEhProcessada_sobeParaADlq() {
        repositorio.iniciar("u-4", "sub-1", AGORA);

        assertThatThrownBy(() -> fn.handleRequest(envelope(Map.of("calibracaoId", "u-4")), null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> fn.handleRequest(envelope(Map.of("calibracaoId", "nao-existe")), null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(gravacoes).isEmpty();
    }

    @Test
    void falhaAoGravarOPdf_naoMarcaEmitida() {
        processada("u-5");
        FnCertificado quebrado = criar((id, pdf) -> {
            throw new IllegalStateException("S3 fora do ar");
        });

        assertThatThrownBy(() -> quebrado.handleRequest(envelope(Map.of("calibracaoId", "u-5")), null))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repositorio.buscar("u-5").orElseThrow().estado()).isEqualTo(EstadoCalibracao.PROCESSADA);
    }

    @Test
    void ambienteSemVariavel_falhaNaInicializacao() {
        assertThatThrownBy(() -> new FnCertificado(nome -> null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TABELA_CALIBRACOES");
    }
}
