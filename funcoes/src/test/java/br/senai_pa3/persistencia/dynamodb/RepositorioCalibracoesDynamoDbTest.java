package br.senai_pa3.persistencia.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.senai_pa3.dominio.PontoEnsaio;
import br.senai_pa3.dominio.ResultadoCalibracao;
import br.senai_pa3.dominio.ResultadoPonto;
import br.senai_pa3.ingestao.ErroValidacao;
import br.senai_pa3.persistencia.ResultadoRegistro;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

/** Cliente DynamoDB falso por {@link Proxy}: respostas roteirizadas e requisições gravadas. */
class RepositorioCalibracoesDynamoDbTest {

    private static final String SHA = "a".repeat(64);
    private static final Instant AGORA = Instant.parse("2026-10-25T12:00:00Z");

    private final List<Object> requisicoes = new ArrayList<>();
    private Function<TransactWriteItemsRequest, TransactWriteItemsResponse> transacao;
    private Function<UpdateItemRequest, UpdateItemResponse> atualizacao;
    private Function<GetItemRequest, GetItemResponse> leitura;
    private RepositorioCalibracoesDynamoDb repo;

    @BeforeEach
    void montar() {
        transacao = r -> TransactWriteItemsResponse.builder().build();
        atualizacao = r -> UpdateItemResponse.builder().build();
        leitura = r -> GetItemResponse.builder().build();
        DynamoDbClient cliente = (DynamoDbClient) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { DynamoDbClient.class }, (proxy, metodo, args) -> {
                    requisicoes.add(args[0]);
                    return switch (metodo.getName()) {
                        case "transactWriteItems" -> transacao.apply((TransactWriteItemsRequest) args[0]);
                        case "updateItem" -> atualizacao.apply((UpdateItemRequest) args[0]);
                        case "getItem" -> leitura.apply((GetItemRequest) args[0]);
                        default -> throw new UnsupportedOperationException(metodo.getName());
                    };
                });
        repo = new RepositorioCalibracoesDynamoDb(cliente, "calibracoes");
    }

    private static ResultadoCalibracao resultado() {
        PontoEnsaio p = new PontoEnsaio("P1", new BigDecimal("10"), new BigDecimal("0.05"), new BigDecimal("-0.05"),
                List.of(new BigDecimal("10.00"), new BigDecimal("10.00")));
        return new ResultadoCalibracao(List.of(new ResultadoPonto(p, new BigDecimal("10.0000000000"))));
    }

    private static TransactionCanceledException cancelada(String codigoSentinela, String codigoMeta, String dono) {
        CancellationReason.Builder sentinela = CancellationReason.builder().code(codigoSentinela);
        if (dono != null) {
            sentinela.item(Map.of("calId", AttributeValue.fromS(dono)));
        }
        return TransactWriteItemsExceptionBuilder.com(sentinela.build(),
                CancellationReason.builder().code(codigoMeta).build());
    }

    @Test
    void hashNovo_enviaSentinelaEMetaNaMesmaTransacao() {
        ResultadoRegistro r = repo.registrarProcessada("u-1", SHA, 2048L, resultado(), AGORA);

        assertThat(r).isEqualTo(new ResultadoRegistro.Registrada());
        TransactWriteItemsRequest req = (TransactWriteItemsRequest) requisicoes.get(0);
        assertThat(req.transactItems()).hasSize(2);
        var put = req.transactItems().get(0).put();
        assertThat(put.item().get("PK").s()).isEqualTo("HASH#" + SHA);
        assertThat(put.item().get("calId").s()).isEqualTo("u-1");
        assertThat(put.conditionExpression()).isEqualTo("attribute_not_exists(PK)");
        var meta = req.transactItems().get(1).update();
        assertThat(meta.key().get("PK").s()).isEqualTo("CAL#u-1");
        assertThat(meta.conditionExpression()).isEqualTo("#e = :anterior");
        assertThat(meta.expressionAttributeValues().get(":anterior").s()).isEqualTo("PROCESSANDO");
        assertThat(meta.expressionAttributeValues().get(":novo").s()).isEqualTo("PROCESSADA");
        assertThat(meta.expressionAttributeValues().get(":resultado").m().get("pontos").l().get(0).m()
                .get("media").s()).isEqualTo("10.0000000000");
    }

    @Test
    void sentinelaDoMesmoId_ehRetomadaESemSegundaEscrita() {
        transacao = r -> {
            throw cancelada("ConditionalCheckFailed", "None", "u-1");
        };

        assertThat(repo.registrarProcessada("u-1", SHA, 2048L, resultado(), AGORA))
                .isEqualTo(new ResultadoRegistro.Retomada());
        assertThat(requisicoes).hasSize(1);
    }

    @Test
    void sentinelaDeOutroId_marcaDuplicadaApontandoParaOOriginal() {
        transacao = r -> {
            throw cancelada("ConditionalCheckFailed", "None", "u-0");
        };

        assertThat(repo.registrarRejeitada("u-1", SHA, 2048L, List.of(ErroValidacao.geral("R03", "x")), AGORA))
                .isEqualTo(new ResultadoRegistro.Duplicada("u-0"));
        UpdateItemRequest up = (UpdateItemRequest) requisicoes.get(1);
        assertThat(up.expressionAttributeValues().get(":novo").s()).isEqualTo("DUPLICADA");
        assertThat(up.expressionAttributeValues().get(":original").s()).isEqualTo("u-0");
        assertThat(up.conditionExpression()).isEqualTo("#e = :anterior");
    }

    @Test
    void donoSemItemNoMotivo_leSentinelaComLeituraConsistente() {
        transacao = r -> {
            throw cancelada("ConditionalCheckFailed", "None", null);
        };
        leitura = r -> GetItemResponse.builder().item(Map.of("calId", AttributeValue.fromS("u-0"))).build();

        assertThat(repo.registrarProcessada("u-1", SHA, 2048L, resultado(), AGORA))
                .isEqualTo(new ResultadoRegistro.Duplicada("u-0"));
        GetItemRequest get = (GetItemRequest) requisicoes.get(1);
        assertThat(get.consistentRead()).isTrue();
        assertThat(get.key().get("PK").s()).isEqualTo("HASH#" + SHA);
    }

    @Test
    void envioForaDeProcessando_lancaIllegalState() {
        transacao = r -> {
            throw cancelada("None", "ConditionalCheckFailed", null);
        };

        assertThatThrownBy(() -> repo.registrarProcessada("u-1", SHA, 2048L, resultado(), AGORA))
                .isInstanceOf(IllegalStateException.class);
    }

    private void leituras(br.senai_pa3.persistencia.EstadoCalibracao estadoAtual, String idOriginal) {
        var registro = new br.senai_pa3.persistencia.RegistroCalibracao("u-1", "s", estadoAtual, SHA, 2048L,
                estadoAtual == br.senai_pa3.persistencia.EstadoCalibracao.PROCESSADA ? resultado() : null, null,
                idOriginal, null, null, AGORA);
        leitura = r -> r.key().get("PK").s().startsWith("HASH#")
                ? GetItemResponse.builder().item(Map.of("calId", AttributeValue.fromS("u-0"))).build()
                : GetItemResponse.builder().item(MapeadorItemCalibracao.paraItem(registro)).build();
    }

    @Test
    void duplicadaRepetida_ehInofensivaMasOutroEstadoFalha() {
        atualizacao = r -> {
            throw ConditionalCheckFailedException.builder().message("x").build();
        };

        leituras(br.senai_pa3.persistencia.EstadoCalibracao.DUPLICADA, "u-0");
        repo.registrarDuplicada("u-1", "u-0", SHA, 2048L, AGORA);

        leituras(br.senai_pa3.persistencia.EstadoCalibracao.PROCESSADA, null);
        assertThatThrownBy(() -> repo.registrarDuplicada("u-1", "u-0", SHA, 2048L, AGORA))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void duplicada_comOriginalQueNaoEhDonoDoHashFalha() {
        leitura = r -> GetItemResponse.builder().item(Map.of("calId", AttributeValue.fromS("u-9"))).build();

        assertThatThrownBy(() -> repo.registrarDuplicada("u-1", "u-0", SHA, 2048L, AGORA))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void emitida_usaSentinelaDeTokenECondicaoProcessada() {
        repo.registrarEmitida("u-1", "tok", "b".repeat(64), AGORA);

        TransactWriteItemsRequest req = (TransactWriteItemsRequest) requisicoes.get(0);
        assertThat(req.transactItems().get(0).put().item().get("PK").s()).isEqualTo("TOKEN#tok");
        assertThat(req.transactItems().get(1).update().expressionAttributeValues().get(":anterior").s())
                .isEqualTo("PROCESSADA");
    }

    @Test
    void emitida_tokenDeOutroEnvioFalha_mesmoTokenRepetidoNao() {
        transacao = r -> {
            throw cancelada("ConditionalCheckFailed", "None", "u-1");
        };
        repo.registrarEmitida("u-1", "tok", "b".repeat(64), AGORA);

        transacao = r -> {
            throw cancelada("ConditionalCheckFailed", "None", "u-0");
        };
        assertThatThrownBy(() -> repo.registrarEmitida("u-1", "tok", "b".repeat(64), AGORA))
                .isInstanceOf(IllegalStateException.class);
    }
}
