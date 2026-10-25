package br.senai_pa3.ingestao;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord;

import br.senai_pa3.dominio.CalculadoraMetrologica;
import br.senai_pa3.dominio.PontoEnsaio;
import br.senai_pa3.dominio.ResultadoCalibracao;
import br.senai_pa3.persistencia.EstadoCalibracao;
import br.senai_pa3.persistencia.RepositorioCalibracoes;
import br.senai_pa3.persistencia.ResultadoRegistro;
import br.senai_pa3.persistencia.dynamodb.RepositorioCalibracoesDynamoDb;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * {@code fn-ingestao}: evento S3 em {@code uploads/<sub>/<uploadId>.xlsx} → hash, leitura, cálculo e
 * gravação pela porta. Injeção por construtor, sem framework (D-017).
 * <p>
 * Planilha inválida e duplicata são resultado de negócio: gravam o estado e terminam com sucesso.
 * Qualquer outra falha sobe como exceção, e o evento vai para a DLQ.
 * <p>
 * 🔒 O log leva só id, SHA-256, tamanho e resultado; nunca a chave, o nome do arquivo nem o conteúdo.
 */
public class FnIngestao implements RequestHandler<S3Event, Map<String, String>> {

    private static final System.Logger log = System.getLogger(FnIngestao.class.getName());
    /** {@code uploads/<sub>/<uploadId>.xlsx}; o {@code sub} do Cognito é opaco e sem barra. */
    static final String VARIAVEL_TABELA = "TABELA_CALIBRACOES";
    private static final Pattern CHAVE = Pattern.compile("uploads/([^/]+)/([^/]+)\\.xlsx");

    private final ArmazenamentoObjetos armazenamento;
    private final RepositorioCalibracoes repositorio;
    private final LeitorPlanilha leitor;
    private final CalculadoraMetrologica calculadora;
    private final Clock relogio;

    /** Chamado pelo Lambda: monta os adaptadores reais. */
    public FnIngestao() {
        this(new S3ArmazenamentoObjetos(), repositorioReal(), new LeitorPlanilhaPoi(),
                new CalculadoraMetrologica(), Clock.systemUTC());
    }

    public FnIngestao(ArmazenamentoObjetos armazenamento, RepositorioCalibracoes repositorio,
            LeitorPlanilha leitor, CalculadoraMetrologica calculadora, Clock relogio) {
        this.armazenamento = armazenamento;
        this.repositorio = repositorio;
        this.leitor = leitor;
        this.calculadora = calculadora;
        this.relogio = relogio;
    }

    @Override
    public Map<String, String> handleRequest(S3Event evento, Context contexto) {
        List<S3EventNotificationRecord> registros = evento.getRecords();
        if (registros == null || registros.size() != 1) {
            throw new IllegalArgumentException("Esperado exatamente um registro no evento S3.");
        }
        var objeto = registros.get(0).getS3();
        String chave = objeto.getObject().getUrlDecodedKey();
        Matcher m = CHAVE.matcher(chave);
        if (!m.matches()) {
            // Sem a chave no texto: ela carrega o sub.
            throw new IllegalArgumentException("Chave fora do padrão uploads/<sub>/<uploadId>.xlsx.");
        }
        String id = m.group(2);
        boolean processada = processar(id, armazenamento.ler(objeto.getBucket().getName(), chave));
        // O destino OnSuccess aciona a fn-certificado com qualquer retorno: só PROCESSADA leva o id.
        return processada ? Map.of("calibracaoId", id) : Map.of();
    }

    /** @return {@code true} se o envio está PROCESSADA (agora ou em reentrega). */
    private boolean processar(String id, byte[] conteudo) {
        Instant agora = relogio.instant();
        String sha256 = sha256(conteudo);
        long bytes = conteudo.length;

        var dono = repositorio.idDoHash(sha256);
        if (dono.isPresent() && !dono.get().equals(id)) {
            // A porta só aceita DUPLICADA a partir de PROCESSANDO: na reentrega, não grava de novo.
            boolean jaDuplicada = repositorio.buscar(id)
                    .map(r -> r.estado() == EstadoCalibracao.DUPLICADA).orElse(false);
            if (!jaDuplicada) {
                repositorio.registrarDuplicada(id, dono.get(), sha256, bytes, agora);
            }
            registrar(id, sha256, bytes, jaDuplicada ? "DUPLICADA (reentrega)" : "DUPLICADA");
            return false;
        }

        ResultadoRegistro registro;
        String desfecho;
        boolean valida;
        try {
            List<PontoEnsaio> pontos = leitor.ler(new ByteArrayInputStream(conteudo));
            ResultadoCalibracao resultado = calculadora.calcular(pontos);
            registro = repositorio.registrarProcessada(id, sha256, bytes, resultado, agora);
            desfecho = "PROCESSADA";
            valida = true;
        } catch (PlanilhaInvalidaException e) {
            registro = repositorio.registrarRejeitada(id, sha256, bytes, e.erros(), agora);
            desfecho = "REJEITADA";
            valida = false;
        }
        if (registro instanceof ResultadoRegistro.Duplicada) {
            desfecho = "DUPLICADA";
            valida = false;
        } else if (registro instanceof ResultadoRegistro.Retomada) {
            desfecho += " (reentrega)";
        }
        registrar(id, sha256, bytes, desfecho);
        return valida;
    }

    private static String sha256(byte[] conteudo) {
        Medidor medidor = new Medidor(new ByteArrayInputStream(conteudo));
        try {
            medidor.consumirRestante();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return medidor.sha256();
    }

    private static void registrar(String id, String sha256, long bytes, String resultado) {
        log.log(System.Logger.Level.INFO, "Ingestão: uploadId={0} sha256={1} bytes={2} resultado={3}",
                id, sha256, bytes, resultado);
    }

    private static RepositorioCalibracoes repositorioReal() {
        return repositorioReal(System::getenv);
    }

    /** O nome da tabela vem do ambiente (definido no template SAM); falta de variável falha na inicialização. */
    static RepositorioCalibracoes repositorioReal(UnaryOperator<String> ambiente) {
        String tabela = ambiente.apply(VARIAVEL_TABELA);
        if (tabela == null || tabela.isBlank()) {
            throw new IllegalStateException("Variável de ambiente " + VARIAVEL_TABELA + " não definida.");
        }
        return new RepositorioCalibracoesDynamoDb(DynamoDbClient.create(), tabela);
    }
}
