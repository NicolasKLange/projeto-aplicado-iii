package br.senai_pa3.persistencia.dynamodb;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.senai_pa3.dominio.ResultadoCalibracao;
import br.senai_pa3.ingestao.ErroValidacao;
import br.senai_pa3.persistencia.EstadoCalibracao;
import br.senai_pa3.persistencia.RegistroCalibracao;
import br.senai_pa3.persistencia.RepositorioCalibracoes;
import br.senai_pa3.persistencia.ResultadoRegistro;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/**
 * Adaptador DynamoDB da porta {@link RepositorioCalibracoes} (docs/av04/modelo-dados.md).
 * <p>
 * Cada transição que cria sentinela é uma {@code TransactWriteItems} de dois itens:
 * [0] {@code Put} da sentinela com {@code attribute_not_exists(PK)} e [1] {@code Update} do
 * {@code META} com condição sobre o estado anterior. O cancelamento é traduzido por
 * {@link DecisorCancelamento}. {@code ALL_OLD} no item da sentinela devolve o dono no próprio
 * motivo do cancelamento, sem segunda leitura.
 * <p>
 * 🔒 Só grava os atributos do modelo; nada de nome de arquivo, conteúdo bruto ou nome do usuário.
 */
public class RepositorioCalibracoesDynamoDb implements RepositorioCalibracoes {

    private static final String PREFIXO_HASH = "HASH#";
    private static final String PREFIXO_TOKEN = "TOKEN#";
    private static final String SK_UNICO = "UNICO";

    private final DynamoDbClient dynamo;
    private final String tabela;

    public RepositorioCalibracoesDynamoDb(DynamoDbClient dynamo, String tabela) {
        this.dynamo = dynamo;
        this.tabela = tabela;
    }

    @Override
    public void iniciar(String id, String sub, Instant agora) {
        RegistroCalibracao novo = new RegistroCalibracao(id, sub, EstadoCalibracao.PROCESSANDO,
                null, null, null, null, null, null, null, agora);
        try {
            dynamo.putItem(PutItemRequest.builder()
                    .tableName(tabela)
                    .item(MapeadorItemCalibracao.paraItem(novo))
                    .conditionExpression("attribute_not_exists(PK)")
                    .build());
        } catch (ConditionalCheckFailedException e) {
            throw new IllegalStateException("Envio já existe.", e);
        }
    }

    @Override
    public Optional<String> idDoHash(String sha256) {
        return donoDa(PREFIXO_HASH + sha256);
    }

    @Override
    public ResultadoRegistro registrarProcessada(String id, String sha256, long tamanhoBytes,
            ResultadoCalibracao resultado, Instant agora) {
        Map<String, AttributeValue> extras = new HashMap<>();
        extras.put(":resultado", MapeadorItemCalibracao.resultado(resultado));
        return registrar(id, sha256, tamanhoBytes, EstadoCalibracao.PROCESSADA, "resultado", extras, agora);
    }

    @Override
    public ResultadoRegistro registrarRejeitada(String id, String sha256, long tamanhoBytes,
            List<ErroValidacao> erros, Instant agora) {
        if (erros.isEmpty()) {
            throw new IllegalArgumentException("REJEITADA exige pelo menos um erro.");
        }
        Map<String, AttributeValue> extras = new HashMap<>();
        extras.put(":erros", MapeadorItemCalibracao.erros(erros));
        return registrar(id, sha256, tamanhoBytes, EstadoCalibracao.REJEITADA, "erros", extras, agora);
    }

    @Override
    public void registrarDuplicada(String id, String idOriginal, String sha256, long tamanhoBytes, Instant agora) {
        if (!id.equals(idOriginal) && !idOriginal.equals(idDoHash(sha256).orElse(null))) {
            throw new IllegalStateException("O original informado não é dono desse hash.");
        }
        marcarDuplicada(id, idOriginal, sha256, tamanhoBytes, agora);
    }

    @Override
    public void registrarEmitida(String id, String token, String sha256Pdf, Instant agora) {
        Map<String, AttributeValue> valores = new HashMap<>();
        valores.put(":novo", s(EstadoCalibracao.EMITIDA.name()));
        valores.put(":anterior", s(EstadoCalibracao.PROCESSADA.name()));
        valores.put(":agora", s(agora.toString()));
        valores.put(":token", s(token));
        valores.put(":sha256Pdf", s(sha256Pdf));
        Update meta = atualizacaoMeta(id,
                "SET #e = :novo, atualizadoEm = :agora, GSI2SK = :agora, #t = :token, sha256Pdf = :sha256Pdf",
                Map.of("#e", "estado", "#t", "token"), valores);

        Optional<DecisorCancelamento.Decisao> cancelada = transacionar(id, PREFIXO_TOKEN + token, meta);
        if (cancelada.isEmpty() || cancelada.get() instanceof DecisorCancelamento.Retomada) {
            return; // gravou, ou emissão repetida com o mesmo token
        }
        if (cancelada.get() instanceof DecisorCancelamento.Duplicada) {
            throw new IllegalStateException("Token já pertence a outro envio.");
        }
        throw falhar((DecisorCancelamento.Erro) cancelada.get());
    }

    @Override
    public Optional<RegistroCalibracao> buscar(String id) {
        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                .tableName(tabela)
                .key(chave(MapeadorItemCalibracao.PREFIXO_CAL + id, MapeadorItemCalibracao.SK_META))
                .consistentRead(true)
                .build()).item();
        return item == null || item.isEmpty() ? Optional.empty() : Optional.of(MapeadorItemCalibracao.deItem(item));
    }

    private ResultadoRegistro registrar(String id, String sha256, long tamanhoBytes, EstadoCalibracao novo,
            String atributo, Map<String, AttributeValue> extras, Instant agora) {
        Map<String, AttributeValue> valores = new HashMap<>(extras);
        valores.put(":novo", s(novo.name()));
        valores.put(":anterior", s(EstadoCalibracao.PROCESSANDO.name()));
        valores.put(":agora", s(agora.toString()));
        valores.put(":sha256", s(sha256));
        valores.put(":tamanho", AttributeValue.fromN(Long.toString(tamanhoBytes)));
        Update meta = atualizacaoMeta(id,
                "SET #e = :novo, atualizadoEm = :agora, GSI2SK = :agora, sha256 = :sha256, "
                        + "tamanhoBytes = :tamanho, " + atributo + " = :" + atributo,
                Map.of("#e", "estado"), valores);

        Optional<DecisorCancelamento.Decisao> cancelada = transacionar(id, PREFIXO_HASH + sha256, meta);
        if (cancelada.isEmpty()) {
            return new ResultadoRegistro.Registrada();
        }
        DecisorCancelamento.Decisao decisao = cancelada.get();
        if (decisao instanceof DecisorCancelamento.Retomada) {
            return new ResultadoRegistro.Retomada();
        }
        if (decisao instanceof DecisorCancelamento.Duplicada d) {
            marcarDuplicada(id, d.idDono(), sha256, tamanhoBytes, agora);
            return new ResultadoRegistro.Duplicada(d.idDono());
        }
        throw falhar((DecisorCancelamento.Erro) decisao);
    }

    /** Executa [sentinela, META]. Vazio = gravou; senão, a decisão sobre o cancelamento. */
    private Optional<DecisorCancelamento.Decisao> transacionar(String id, String chaveSentinela, Update meta) {
        Put sentinela = Put.builder()
                .tableName(tabela)
                .item(Map.of("PK", s(chaveSentinela), "SK", s(SK_UNICO), "calId", s(id)))
                .conditionExpression("attribute_not_exists(PK)")
                .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)
                .build();
        try {
            dynamo.transactWriteItems(TransactWriteItemsRequest.builder()
                    .transactItems(TransactWriteItem.builder().put(sentinela).build(),
                            TransactWriteItem.builder().update(meta).build())
                    .build());
            return Optional.empty();
        } catch (TransactionCanceledException e) {
            List<CancellationReason> motivos = e.cancellationReasons();
            List<String> codigos = motivos.stream().map(CancellationReason::code).toList();
            Optional<String> dono = Optional.empty();
            if (!motivos.isEmpty() && motivos.get(0).hasItem() && motivos.get(0).item().containsKey("calId")) {
                dono = Optional.of(motivos.get(0).item().get("calId").s());
            } else if (!motivos.isEmpty() && DecisorCancelamento.CONDICAO_FALHOU.equals(codigos.get(0))) {
                dono = donoDa(chaveSentinela);
            }
            return Optional.of(DecisorCancelamento.decidir(id, codigos, dono));
        }
    }

    private void marcarDuplicada(String id, String idOriginal, String sha256, long tamanhoBytes, Instant agora) {
        Map<String, AttributeValue> valores = new HashMap<>();
        valores.put(":novo", s(EstadoCalibracao.DUPLICADA.name()));
        valores.put(":anterior", s(EstadoCalibracao.PROCESSANDO.name()));
        valores.put(":agora", s(agora.toString()));
        valores.put(":sha256", s(sha256));
        valores.put(":tamanho", AttributeValue.fromN(Long.toString(tamanhoBytes)));
        valores.put(":original", s(idOriginal));
        try {
            dynamo.updateItem(UpdateItemRequest.builder()
                    .tableName(tabela)
                    .key(chave(MapeadorItemCalibracao.PREFIXO_CAL + id, MapeadorItemCalibracao.SK_META))
                    .updateExpression("SET #e = :novo, atualizadoEm = :agora, GSI2SK = :agora, sha256 = :sha256, "
                            + "tamanhoBytes = :tamanho, idOriginal = :original")
                    .conditionExpression("#e = :anterior")
                    .expressionAttributeNames(Map.of("#e", "estado"))
                    .expressionAttributeValues(valores)
                    .build());
        } catch (ConditionalCheckFailedException e) {
            // Evento repetido: já está DUPLICADA do mesmo original. Qualquer outro estado é erro.
            boolean igual = buscar(id)
                    .filter(r -> r.estado() == EstadoCalibracao.DUPLICADA && idOriginal.equals(r.idOriginal()))
                    .isPresent();
            if (!igual) {
                throw new IllegalStateException("Só PROCESSANDO aceita essa transição.", e);
            }
        }
    }

    private Update atualizacaoMeta(String id, String expressao, Map<String, String> nomes,
            Map<String, AttributeValue> valores) {
        return Update.builder()
                .tableName(tabela)
                .key(chave(MapeadorItemCalibracao.PREFIXO_CAL + id, MapeadorItemCalibracao.SK_META))
                .updateExpression(expressao)
                .conditionExpression("#e = :anterior")
                .expressionAttributeNames(nomes)
                .expressionAttributeValues(valores)
                .build();
    }

    private Optional<String> donoDa(String chaveSentinela) {
        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                .tableName(tabela)
                .key(chave(chaveSentinela, SK_UNICO))
                .consistentRead(true)
                .build()).item();
        return item == null || !item.containsKey("calId") ? Optional.empty() : Optional.of(item.get("calId").s());
    }

    private static RuntimeException falhar(DecisorCancelamento.Erro e) {
        if (e.transitorio()) {
            return new IllegalStateException("Falha transitória, repetir o evento: " + e.motivo());
        }
        return new IllegalStateException(e.motivo());
    }

    private static Map<String, AttributeValue> chave(String pk, String sk) {
        return Map.of("PK", s(pk), "SK", s(sk));
    }

    private static AttributeValue s(String v) {
        return AttributeValue.fromS(v);
    }
}
