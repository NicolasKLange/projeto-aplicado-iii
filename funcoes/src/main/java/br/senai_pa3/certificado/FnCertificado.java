package br.senai_pa3.certificado;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.UnaryOperator;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import br.senai_pa3.persistencia.EstadoCalibracao;
import br.senai_pa3.persistencia.RegistroCalibracao;
import br.senai_pa3.persistencia.RepositorioCalibracoes;
import br.senai_pa3.persistencia.dynamodb.RepositorioCalibracoesDynamoDb;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * {@code fn-certificado}: recebe o envelope do destino OnSuccess da {@code fn-ingestao}, gera o PDF,
 * grava no S3 e passa a calibração para EMITIDA. Injeção por construtor, sem framework (D-017).
 * <p>
 * O destino dispara com qualquer retorno da {@code fn-ingestao}: envelope sem {@code calibracaoId}
 * (REJEITADA, DUPLICADA) é ignorado com sucesso (D-023). Reentrega de uma calibração já EMITIDA também.
 * <p>
 * 🔒 O nome de exibição vem do diretório de usuários só para o PDF. O log leva só id, SHA-256 do PDF e
 * resultado; nunca o token, o nome nem o conteúdo.
 */
public class FnCertificado implements RequestHandler<Map<String, Object>, Map<String, String>> {

    private static final System.Logger log = System.getLogger(FnCertificado.class.getName());
    static final String VARIAVEL_TABELA = "TABELA_CALIBRACOES";
    static final String VARIAVEL_BUCKET = "BUCKET_CERTIFICADOS";
    static final String VARIAVEL_URL = "URL_VERIFICACAO_BASE";
    static final String VARIAVEL_USER_POOL = "USER_POOL_ID";
    /** Só se o usuário não tiver nome no diretório; a tabela guarda apenas o {@code sub} (RN-35). */
    static final String GERADO_POR_PADRAO = "Usuário autenticado";
    private static final String ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    static final int TAMANHO_TOKEN = 32;

    private final RepositorioCalibracoes repositorio;
    private final ArmazenamentoCertificados armazenamento;
    private final ConsultaUsuarios usuarios;
    private final GeradorCertificado gerador;
    private final Clock relogio;
    private final SecureRandom aleatorio;
    private final String urlBase;

    /** Chamado pelo Lambda: monta os adaptadores reais. */
    public FnCertificado() {
        this(System::getenv);
    }

    FnCertificado(UnaryOperator<String> ambiente) {
        this(new Ambiente(exigir(ambiente, VARIAVEL_TABELA), exigir(ambiente, VARIAVEL_BUCKET),
                exigir(ambiente, VARIAVEL_USER_POOL), exigir(ambiente, VARIAVEL_URL)));
    }

    /**
     * Variáveis conferidas antes de criar os clientes do SDK: os argumentos são avaliados da esquerda
     * para a direita, e um {@code create()} sem região (fora do Lambda) falharia antes da conferência.
     */
    private record Ambiente(String tabela, String bucket, String userPool, String urlBase) {
    }

    private FnCertificado(Ambiente a) {
        this(new RepositorioCalibracoesDynamoDb(DynamoDbClient.create(), a.tabela()),
                new S3ArmazenamentoCertificados(S3Client.create(), a.bucket()),
                new CognitoConsultaUsuarios(CognitoIdentityProviderClient.create(), a.userPool()),
                new GeradorCertificado(), Clock.systemUTC(), new SecureRandom(), a.urlBase());
    }

    public FnCertificado(RepositorioCalibracoes repositorio, ArmazenamentoCertificados armazenamento,
            ConsultaUsuarios usuarios, GeradorCertificado gerador, Clock relogio, SecureRandom aleatorio, String urlBase) {
        this.repositorio = repositorio;
        this.armazenamento = armazenamento;
        this.usuarios = usuarios;
        this.gerador = gerador;
        this.relogio = relogio;
        this.aleatorio = aleatorio;
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
    }

    @Override
    public Map<String, String> handleRequest(Map<String, Object> envelope, Context contexto) {
        String id = calibracaoId(envelope);
        if (id == null) {
            log.log(System.Logger.Level.INFO, "Emissão: envelope sem calibracaoId, ignorado");
            return Map.of();
        }
        RegistroCalibracao registro = repositorio.buscar(id)
                .orElseThrow(() -> new IllegalStateException("Calibração não existe: " + id));
        if (registro.estado() == EstadoCalibracao.EMITIDA) {
            log.log(System.Logger.Level.INFO, "Emissão: uploadId={0} resultado=EMITIDA (reentrega)", id);
            return Map.of("calibracaoId", id);
        }
        if (registro.estado() != EstadoCalibracao.PROCESSADA) {
            throw new IllegalStateException("Só PROCESSADA pode ser emitida: " + id);
        }

        Instant agora = relogio.instant();
        String geradoPor = usuarios.nomeDeExibicao(registro.sub()).orElse(GERADO_POR_PADRAO);
        String token = novoToken();
        byte[] pdf = gerador.gerar(new DadosCertificado(id, agora, registro.sha256(), geradoPor,
                registro.resultado(), token, urlBase + "/verificar/" + token));
        String sha256Pdf = sha256(pdf);
        armazenamento.gravar(id, pdf);
        repositorio.registrarEmitida(id, token, sha256Pdf, agora);

        log.log(System.Logger.Level.INFO, "Emissão: uploadId={0} sha256Pdf={1} bytes={2} resultado=EMITIDA",
                id, sha256Pdf, String.valueOf(pdf.length));
        return Map.of("calibracaoId", id);
    }

    /** {@code responsePayload.calibracaoId} do destino Lambda; {@code null} se faltar ou vier vazio. */
    private static String calibracaoId(Map<String, Object> envelope) {
        if (envelope != null && envelope.get("responsePayload") instanceof Map<?, ?> payload
                && payload.get("calibracaoId") instanceof String id && !id.isBlank()) {
            return id;
        }
        return null;
    }

    private String novoToken() {
        StringBuilder sb = new StringBuilder(TAMANHO_TOKEN);
        for (int i = 0; i < TAMANHO_TOKEN; i++) {
            sb.append(ALFABETO.charAt(aleatorio.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    private static String sha256(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String exigir(UnaryOperator<String> ambiente, String nome) {
        String valor = ambiente.apply(nome);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Variável de ambiente " + nome + " não definida.");
        }
        return valor;
    }
}
