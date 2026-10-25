package br.senai_pa3.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent.RequestContext;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import br.senai_pa3.dominio.PontoEnsaio;
import br.senai_pa3.dominio.ResultadoCalibracao;
import br.senai_pa3.dominio.ResultadoPonto;
import br.senai_pa3.ingestao.ErroValidacao;
import br.senai_pa3.persistencia.RepositorioCalibracoes;
import br.senai_pa3.persistencia.RepositorioEmMemoria;

/** Sem AWS: repositório em memória e URLs pré-assinadas falsas. Só dados sintéticos. */
class FnApiTest {

    private static final Instant AGORA = Instant.parse("2026-10-25T12:00:00Z");
    private static final String SUB = "sub-opaco-1";
    private static final String OUTRO_SUB = "sub-opaco-2";
    private static final String ID = "11111111-1111-4111-8111-111111111111";
    private static final String SHA = "a".repeat(64);
    private static final String TOKEN = "T".repeat(32);

    private RepositorioEmMemoria repositorio;
    private FnApi api;

    @BeforeEach
    void montar() {
        repositorio = new RepositorioEmMemoria();
        api = criar(repositorio);
    }

    private static FnApi criar(RepositorioCalibracoes repositorio) {
        UrlsPreassinadas urls = new UrlsPreassinadas() {
            @Override
            public String urlEnvio(String sub, String uploadId, long tamanhoBytes) {
                return "https://s3.exemplo/envio/" + uploadId + "?bytes=" + tamanhoBytes;
            }

            @Override
            public String urlCertificado(String id) {
                return "https://s3.exemplo/pdf/" + id;
            }
        };
        return new FnApi(repositorio, urls, Clock.fixed(AGORA, ZoneOffset.UTC), () -> ID);
    }

    private static APIGatewayV2HTTPEvent evento(String rota, String sub, Map<String, String> caminho, String corpo) {
        RequestContext.Authorizer.JWT jwt = new RequestContext.Authorizer.JWT();
        jwt.setClaims(sub == null ? Map.of() : Map.of("sub", sub));
        RequestContext.Authorizer autorizador = new RequestContext.Authorizer();
        autorizador.setJwt(jwt);
        RequestContext contexto = new RequestContext();
        contexto.setAuthorizer(autorizador);
        return APIGatewayV2HTTPEvent.builder().withRouteKey(rota).withPathParameters(caminho).withBody(corpo)
                .withRequestContext(contexto).build();
    }

    private APIGatewayV2HTTPResponse chamar(String rota, String sub, Map<String, String> caminho, String corpo) {
        return api.handleRequest(evento(rota, sub, caminho, corpo), null);
    }

    private static ResultadoCalibracao resultado(String media) {
        PontoEnsaio p = new PontoEnsaio("P1", new BigDecimal("10"), new BigDecimal("0.05"),
                new BigDecimal("-0.05"), List.of(new BigDecimal("10.00"), new BigDecimal("10.02")));
        return new ResultadoCalibracao(List.of(new ResultadoPonto(p, new BigDecimal(media))));
    }

    private void emitida(String id, String sub) {
        repositorio.iniciar(id, sub, AGORA);
        repositorio.registrarProcessada(id, SHA, 2048L, resultado("10.0100000000"), AGORA);
        repositorio.registrarEmitida(id, TOKEN, "b".repeat(64), AGORA.plusSeconds(5));
    }

    // POST /uploads

    @Test
    void upload_criaProcessandoEDevolveUrlPut() {
        var r = chamar("POST /uploads", SUB, Map.of(), "{\"tamanho\": 2048}");

        assertThat(r.getStatusCode()).isEqualTo(201);
        assertThat(r.getBody()).contains("\"uploadId\":\"" + ID + "\"", "?bytes=2048", "\"metodo\":\"PUT\"",
                "\"contentType\":\"" + UrlsPreassinadas.TIPO_XLSX + "\"", "\"expiraEmSegundos\":300");
        var registro = repositorio.buscar(ID).orElseThrow();
        assertThat(registro.sub()).isEqualTo(SUB);
        assertThat(registro.estado().name()).isEqualTo("PROCESSANDO");
    }

    @Test
    void upload_acimaDe5MbRecusa413_exatamente5MbAceita() {
        var grande = chamar("POST /uploads", SUB, Map.of(), "{\"tamanho\": " + (5L * 1024 * 1024 + 1) + "}");
        assertThat(grande.getStatusCode()).isEqualTo(413);
        assertThat(grande.getHeaders()).containsEntry("Content-Type", "application/problem+json");
        assertThat(repositorio.buscar(ID)).isEmpty();

        assertThat(chamar("POST /uploads", SUB, Map.of(), "{\"tamanho\": " + (5L * 1024 * 1024) + "}")
                .getStatusCode()).isEqualTo(201);
    }

    @Test
    void upload_corpoInvalido_400() {
        for (String corpo : new String[] { null, "", "{}", "{\"tamanho\": \"10\"}", "{\"tamanho\": 0}",
                "{\"tamanho\": -5}", "{\"tamanho\": 1.5}", "{\"tamanho\": 99999999999999999999}", "nao é json" }) {
            var r = chamar("POST /uploads", SUB, Map.of(), corpo);
            assertThat(r.getStatusCode()).as("corpo %s", corpo).isEqualTo(400);
            assertThat(r.getHeaders()).containsEntry("Content-Type", "application/problem+json");
        }
        assertThat(repositorio.buscar(ID)).isEmpty();
    }

    @Test
    void upload_semSub_401() {
        assertThat(chamar("POST /uploads", null, Map.of(), "{\"tamanho\": 10}").getStatusCode()).isEqualTo(401);
    }

    // GET /calibracoes/{id}

    @Test
    void estado_processadaDevolveResultadoComEscalaCompleta() {
        repositorio.iniciar(ID, SUB, AGORA);
        repositorio.registrarProcessada(ID, SHA, 2048L, resultado("10.0100000000"), AGORA);

        var r = chamar("GET /calibracoes/{id}", SUB, Map.of("id", ID), null);

        assertThat(r.getStatusCode()).isEqualTo(200);
        assertThat(r.getBody()).contains("\"estado\":\"PROCESSADA\"", "\"parecer\":\"APROVADO\"",
                "\"status\":\"DENTRO_DA_TOLERANCIA\"", "\"media\":\"10.0100000000\"",
                "\"erroSistematico\":\"0.0100000000\"", "\"lea\":\"0.05\"");
        assertThat(r.getBody()).doesNotContain(SUB, "token");
    }

    @Test
    void estado_rejeitadaDevolveErros() {
        repositorio.iniciar(ID, SUB, AGORA);
        repositorio.registrarRejeitada(ID, SHA, 10L,
                List.of(new ErroValidacao(3, "Leitura1", "R10", "Campo \"obrigatório\".")), AGORA);

        var r = chamar("GET /calibracoes/{id}", SUB, Map.of("id", ID), null);

        assertThat(r.getBody()).contains("\"estado\":\"REJEITADA\"", "\"regra\":\"R10\"", "\"linha\":3",
                "Campo \\\"obrigatório\\\".");
    }

    @Test
    void estado_duplicadaNaoRevelaOIdDoOriginal() {
        repositorio.iniciar("original", OUTRO_SUB, AGORA);
        repositorio.registrarProcessada("original", SHA, 10L, resultado("10.0100000000"), AGORA);
        repositorio.iniciar(ID, SUB, AGORA);
        repositorio.registrarDuplicada(ID, "original", SHA, 10L, AGORA);

        var r = chamar("GET /calibracoes/{id}", SUB, Map.of("id", ID), null);

        assertThat(r.getBody()).contains("\"estado\":\"DUPLICADA\"").doesNotContain("original", OUTRO_SUB);
    }

    @Test
    void estado_deOutroSubOuInexistente_ambos404IguaisSem403() {
        repositorio.iniciar(ID, OUTRO_SUB, AGORA);

        var alheia = chamar("GET /calibracoes/{id}", SUB, Map.of("id", ID), null);
        var inexistente = chamar("GET /calibracoes/{id}", SUB,
                Map.of("id", "22222222-2222-4222-8222-222222222222"), null);

        assertThat(alheia.getStatusCode()).isEqualTo(404);
        assertThat(inexistente.getStatusCode()).isEqualTo(404);
        assertThat(alheia.getBody()).isEqualTo(inexistente.getBody());
    }

    @Test
    void estado_idMalformado_400() {
        for (String id : new String[] { "u-1", "../x", "", "11111111-1111-4111-8111-11111111111G" }) {
            assertThat(chamar("GET /calibracoes/{id}", SUB, Map.of("id", id), null).getStatusCode())
                    .as(id).isEqualTo(400);
        }
        assertThat(chamar("GET /calibracoes/{id}", SUB, Map.of(), null).getStatusCode()).isEqualTo(400);
    }

    // GET /calibracoes/{id}/pdf

    @Test
    void pdf_emitidaDevolveUrlGet() {
        emitida(ID, SUB);

        var r = chamar("GET /calibracoes/{id}/pdf", SUB, Map.of("id", ID), null);

        assertThat(r.getStatusCode()).isEqualTo(200);
        assertThat(r.getBody()).contains("\"url\":\"https://s3.exemplo/pdf/" + ID + "\"");
    }

    @Test
    void pdf_antesDaEmissao409_deOutroSub404() {
        repositorio.iniciar(ID, SUB, AGORA);
        assertThat(chamar("GET /calibracoes/{id}/pdf", SUB, Map.of("id", ID), null).getStatusCode()).isEqualTo(409);

        emitida("22222222-2222-4222-8222-222222222222", OUTRO_SUB);
        assertThat(chamar("GET /calibracoes/{id}/pdf", SUB, Map.of("id", "22222222-2222-4222-8222-222222222222"),
                null).getStatusCode()).isEqualTo(404);
    }

    // GET /verificacao/{token}

    @Test
    void verificacao_publicaSemSubNemNome() {
        emitida(ID, SUB);

        var r = chamar("GET /verificacao/{token}", null, Map.of("token", TOKEN), null);

        assertThat(r.getStatusCode()).isEqualTo(200);
        assertThat(r.getBody()).contains("\"id\":\"" + ID + "\"", "\"emitidoEm\":\"2026-10-25T12:00:05Z\"",
                "\"parecer\":\"APROVADO\"", "\"sha256Pdf\":\"" + "b".repeat(64) + "\"");
        assertThat(r.getBody()).doesNotContain(SUB, "pontos", "sha256\"");
    }

    @Test
    void verificacao_tokenDesconhecidoOuMalformado_404() {
        emitida(ID, SUB);
        for (String token : new String[] { "X".repeat(32), "curto", "../..", "T".repeat(33) }) {
            assertThat(chamar("GET /verificacao/{token}", null, Map.of("token", token), null).getStatusCode())
                    .as(token).isEqualTo(404);
        }
    }

    // Outras rotas e erros

    @Test
    void listagemEhStubDaT12_eRotaDesconhecida404() {
        assertThat(chamar("GET /calibracoes", SUB, Map.of(), null).getStatusCode()).isEqualTo(501);
        assertThat(chamar("DELETE /calibracoes/{id}", SUB, Map.of("id", ID), null).getStatusCode()).isEqualTo(404);
    }

    @Test
    void excecaoInesperada_500ComIdSemVazarDetalhes() {
        RepositorioCalibracoes quebrado = new RepositorioEmMemoria() {
            @Override
            public synchronized java.util.Optional<br.senai_pa3.persistencia.RegistroCalibracao> buscar(String id) {
                throw new IllegalStateException("tabela secreta uploads/" + SUB);
            }
        };

        var r = criar(quebrado).handleRequest(
                evento("GET /calibracoes/{id}", SUB, Map.of("id", ID), null), null);

        assertThat(r.getStatusCode()).isEqualTo(500);
        assertThat(r.getHeaders()).containsEntry("Content-Type", "application/problem+json");
        assertThat(r.getBody()).containsPattern("\"id\":\"[0-9a-f-]{36}\"").doesNotContain("secreta", SUB);
    }

    @Test
    void log_naoTemSubUrlNemChave() {
        List<String> linhas = new ArrayList<>();
        Handler captura = new Handler() {
            @Override
            public void publish(LogRecord r) {
                linhas.add(new SimpleFormatter().formatMessage(r));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger logger = Logger.getLogger(FnApi.class.getName());
        logger.addHandler(captura);
        try {
            chamar("POST /uploads", SUB, Map.of(), "{\"tamanho\": 2048}");
            criar(new RepositorioEmMemoria() {
                @Override
                public synchronized java.util.Optional<br.senai_pa3.persistencia.RegistroCalibracao> buscar(
                        String id) {
                    throw new IllegalStateException("uploads/" + SUB + "/x.xlsx");
                }
            }).handleRequest(evento("GET /calibracoes/{id}", SUB, Map.of("id", ID), null), null);
        } finally {
            logger.removeHandler(captura);
        }

        String saida = String.join("\n", linhas);
        assertThat(linhas).hasSize(2);
        assertThat(saida).contains("uploadId=" + ID, "bytes=2048");
        assertThat(saida).doesNotContain(SUB, "https", "uploads/", ".xlsx");
    }
}
