package br.senai_pa3.ingestao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3BucketEntity;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3Entity;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3ObjectEntity;

import br.senai_pa3.dominio.CalculadoraMetrologica;
import br.senai_pa3.persistencia.EstadoCalibracao;
import br.senai_pa3.persistencia.RegistroCalibracao;
import br.senai_pa3.persistencia.RepositorioEmMemoria;

/** Sem AWS: S3 é um Map na memória e o repositório é o adaptador em memória do núcleo. Só planilhas sintéticas. */
class FnIngestaoTest {

    private static final String BUCKET = "bucket-de-teste";
    private static final String SUB = "sub-opaco-1";
    private static final Instant AGORA = Instant.parse("2026-10-25T12:00:00Z");

    private final Map<String, byte[]> objetos = new HashMap<>();
    private final RepositorioEmMemoria repositorio = new RepositorioEmMemoria();
    private FnIngestao fn;

    @BeforeEach
    void montar() {
        fn = criar((bucket, chave) -> objetos.get(bucket + "/" + chave));
    }

    private FnIngestao criar(ArmazenamentoObjetos armazenamento) {
        return new FnIngestao(armazenamento, repositorio, new LeitorPlanilhaPoi(), new CalculadoraMetrologica(),
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    void valida_gravaProcessadaEDevolveOUploadId() throws IOException {
        byte[] planilha = planilha("ok-aprovado.xlsx");
        enviar("u-1", planilha);

        Map<String, String> retorno = fn.handleRequest(evento("u-1"), null);

        assertThat(retorno).containsExactly(Map.entry("calibracaoId", "u-1"));
        RegistroCalibracao r = repositorio.buscar("u-1").orElseThrow();
        assertThat(r.estado()).isEqualTo(EstadoCalibracao.PROCESSADA);
        assertThat(r.sha256()).matches("[0-9a-f]{64}");
        assertThat(r.tamanhoBytes()).isEqualTo(planilha.length);
        assertThat(r.resultado()).isNotNull();
        assertThat(r.atualizadoEm()).isEqualTo(AGORA);
    }

    @Test
    void invalida_r10_gravaRejeitadaETerminaComSucesso() throws IOException {
        enviar("u-2", planilha("inv-R10-obrigatorio.xlsx"));

        Map<String, String> retorno = fn.handleRequest(evento("u-2"), null);

        assertThat(retorno).isEmpty();
        RegistroCalibracao r = repositorio.buscar("u-2").orElseThrow();
        assertThat(r.estado()).isEqualTo(EstadoCalibracao.REJEITADA);
        assertThat(r.erros()).isNotEmpty().allMatch(e -> e.regra().equals("R10"));
        assertThat(r.resultado()).isNull();
    }

    @Test
    void duplicada_apontaParaOEnvioOriginal() throws IOException {
        byte[] planilha = planilha("ok-aprovado.xlsx");
        enviar("u-3", planilha);
        enviar("u-4", planilha);

        fn.handleRequest(evento("u-3"), null);
        Map<String, String> retorno = fn.handleRequest(evento("u-4"), null);

        assertThat(retorno).isEmpty();
        RegistroCalibracao r = repositorio.buscar("u-4").orElseThrow();
        assertThat(r.estado()).isEqualTo(EstadoCalibracao.DUPLICADA);
        assertThat(r.idOriginal()).isEqualTo("u-3");
        assertThat(repositorio.buscar("u-3").orElseThrow().estado()).isEqualTo(EstadoCalibracao.PROCESSADA);
    }

    @Test
    void reentregaDeDuplicada_naoLancaENaoAlteraOEnvio() throws IOException {
        byte[] planilha = planilha("ok-aprovado.xlsx");
        enviar("u-3", planilha);
        enviar("u-4", planilha);
        fn.handleRequest(evento("u-3"), null);
        fn.handleRequest(evento("u-4"), null);
        RegistroCalibracao antes = repositorio.buscar("u-4").orElseThrow();

        Map<String, String> retorno = fn.handleRequest(evento("u-4"), null);

        assertThat(retorno).isEmpty();
        assertThat(repositorio.buscar("u-4")).contains(antes);
    }

    @Test
    void eventoRepetido_naoAlteraOEnvio() throws IOException {
        enviar("u-5", planilha("ok-aprovado.xlsx"));

        fn.handleRequest(evento("u-5"), null);
        RegistroCalibracao antes = repositorio.buscar("u-5").orElseThrow();
        fn.handleRequest(evento("u-5"), null);

        assertThat(repositorio.buscar("u-5")).contains(antes);
    }

    @Test
    void excecaoInesperada_sobeParaADlq() {
        repositorio.iniciar("u-6", SUB, AGORA);
        FnIngestao quebrado = criar((bucket, chave) -> {
            throw new UncheckedIOException(new IOException("S3 fora do ar"));
        });

        assertThatThrownBy(() -> quebrado.handleRequest(evento("u-6"), null))
                .isInstanceOf(UncheckedIOException.class);
        assertThat(repositorio.buscar("u-6").orElseThrow().estado()).isEqualTo(EstadoCalibracao.PROCESSANDO);
    }

    @Test
    void chaveForaDoPadrao_lancaSemVazarAChave() {
        S3Event evento = eventoDaChave("uploads/outro-prefixo/segredo.pdf");

        assertThatThrownBy(() -> fn.handleRequest(evento, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("segredo");
    }

    @Test
    void log_naoTemSubNemChave() throws IOException {
        byte[] planilha = planilha("ok-aprovado.xlsx");
        enviar("u-7", planilha);
        enviar("u-8", planilha);
        enviar("u-9", planilha("inv-R10-obrigatorio.xlsx"));
        List<String> linhas = new ArrayList<>();
        Handler captura = new Handler() {
            @Override
            public void publish(LogRecord r) {
                linhas.add(new java.util.logging.SimpleFormatter().formatMessage(r));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger logger = Logger.getLogger(FnIngestao.class.getName());
        logger.addHandler(captura);
        try {
            fn.handleRequest(evento("u-7"), null);
            fn.handleRequest(evento("u-8"), null);
            fn.handleRequest(evento("u-9"), null);
        } finally {
            logger.removeHandler(captura);
        }

        String saida = String.join("\n", linhas);
        assertThat(linhas).hasSize(3);
        assertThat(saida).contains("uploadId=u-7", "sha256=", "bytes=", "resultado=PROCESSADA");
        assertThat(saida).doesNotContain(SUB).doesNotContain("uploads/").doesNotContain(".xlsx")
                .doesNotContain(BUCKET);
    }

    /** Cria o envio em PROCESSANDO (papel da fn-api) e põe o arquivo no "S3". */
    private void enviar(String id, byte[] conteudo) {
        repositorio.iniciar(id, SUB, AGORA);
        objetos.put(BUCKET + "/uploads/" + SUB + "/" + id + ".xlsx", conteudo);
    }

    private static S3Event evento(String id) {
        return eventoDaChave("uploads/" + SUB + "/" + id + ".xlsx");
    }

    private static S3Event eventoDaChave(String chave) {
        var entidade = new S3Entity(null, new S3BucketEntity(BUCKET, null, null),
                new S3ObjectEntity(chave, 1L, null, null, null), "1.0");
        var registro = new S3EventNotificationRecord(null, "ObjectCreated:Put", null, null, null, null, null,
                entidade, null);
        return new S3Event(List.of(registro));
    }

    private byte[] planilha(String arquivo) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/planilhas/" + arquivo)) {
            return in.readAllBytes();
        }
    }
}
