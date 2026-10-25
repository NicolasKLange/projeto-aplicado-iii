package br.senai_pa3.api;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import br.senai_pa3.dominio.ResultadoCalibracao;
import br.senai_pa3.dominio.ResultadoPonto;
import br.senai_pa3.persistencia.EstadoCalibracao;
import br.senai_pa3.persistencia.RegistroCalibracao;
import br.senai_pa3.persistencia.RepositorioCalibracoes;
import br.senai_pa3.persistencia.dynamodb.RepositorioCalibracoesDynamoDb;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * {@code fn-api}: um handler para o HTTP API v2, com roteamento por {@code routeKey}. O dono é sempre o
 * {@code sub} do JWT validado pelo autorizador; um envio de outro {@code sub} responde 404, igual a um
 * inexistente, para não confirmar a existência. Erros saem em {@code application/problem+json}.
 * <p>
 * 🔒 O log leva só id, tamanho e resultado; nunca o JWT, a URL pré-assinada nem a chave do objeto. O 500
 * devolve só um {@code id} que liga ao log. {@code idOriginal} de duplicata não sai na API: pode ser de
 * outro usuário.
 */
public class FnApi implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final System.Logger log = System.getLogger(FnApi.class.getName());
    static final String VARIAVEL_TABELA = "TABELA_CALIBRACOES";
    static final String VARIAVEL_BUCKET_ENTRADA = "BUCKET_ENTRADA";
    static final String VARIAVEL_BUCKET_CERTIFICADOS = "BUCKET_CERTIFICADOS";
    static final long LIMITE_BYTES = 5L * 1024 * 1024;

    private static final Pattern ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9]{32}");
    private static final Pattern TAMANHO = Pattern.compile("\"tamanho\"\\s*:\\s*(\\d{1,18})\\s*[,}]");
    private static final String PROBLEM_JSON = "application/problem+json";

    private final RepositorioCalibracoes repositorio;
    private final UrlsPreassinadas urls;
    private final Clock relogio;
    private final Supplier<String> geradorDeId;

    /** Chamado pelo Lambda: monta os adaptadores reais. */
    public FnApi() {
        this(System::getenv);
    }

    FnApi(UnaryOperator<String> ambiente) {
        this(new RepositorioCalibracoesDynamoDb(DynamoDbClient.create(), exigir(ambiente, VARIAVEL_TABELA)),
                new S3UrlsPreassinadas(S3Presigner.create(), exigir(ambiente, VARIAVEL_BUCKET_ENTRADA),
                        exigir(ambiente, VARIAVEL_BUCKET_CERTIFICADOS)),
                Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    public FnApi(RepositorioCalibracoes repositorio, UrlsPreassinadas urls, Clock relogio,
            Supplier<String> geradorDeId) {
        this.repositorio = repositorio;
        this.urls = urls;
        this.relogio = relogio;
        this.geradorDeId = geradorDeId;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent evento, Context contexto) {
        try {
            String rota = evento.getRouteKey() == null ? "" : evento.getRouteKey();
            Map<String, String> caminho = evento.getPathParameters() == null ? Map.of() : evento.getPathParameters();
            return switch (rota) {
                case "POST /uploads" -> iniciarEnvio(evento);
                case "GET /calibracoes/{id}" -> comDono(evento, caminho.get("id"), this::estado);
                case "GET /calibracoes/{id}/pdf" -> comDono(evento, caminho.get("id"), this::pdf);
                case "GET /calibracoes" -> problema(501, "Não implementado", "A listagem ainda não está disponível.");
                case "GET /verificacao/{token}" -> verificar(caminho.get("token"));
                default -> problema(404, "Rota inexistente", "Rota não encontrada.");
            };
        } catch (RuntimeException e) {
            String id = UUID.randomUUID().toString();
            // Sem mensagem nem stack: podem carregar a chave do objeto (que leva o sub).
            log.log(System.Logger.Level.ERROR, "Erro inesperado: id={0} tipo={1}", id, e.getClass().getName());
            Map<String, Object> corpo = problemaCorpo(500, "Erro inesperado",
                    "Erro inesperado no processamento. Informe o id ao suporte.");
            corpo.put("id", id);
            return resposta(500, PROBLEM_JSON, Json.escrever(corpo));
        }
    }

    private APIGatewayV2HTTPResponse iniciarEnvio(APIGatewayV2HTTPEvent evento) {
        String sub = sub(evento);
        if (sub == null) {
            return problema(401, "Não autenticado", "Token sem identificação de usuário.");
        }
        Matcher m = TAMANHO.matcher(corpo(evento));
        if (!m.find()) {
            return problema(400, "Requisição inválida", "Informe o tamanho do arquivo em bytes: {\"tamanho\": n}.");
        }
        long tamanho = Long.parseLong(m.group(1));
        if (tamanho <= 0) {
            return problema(400, "Requisição inválida", "O tamanho deve ser maior que zero.");
        }
        if (tamanho > LIMITE_BYTES) {
            return problema(413, "Arquivo grande demais", "O arquivo excede o limite de 5 MB.");
        }
        String id = geradorDeId.get();
        repositorio.iniciar(id, sub, relogio.instant());
        String url = urls.urlEnvio(sub, id, tamanho);
        log.log(System.Logger.Level.INFO, "Upload autorizado: uploadId={0} bytes={1}", id, String.valueOf(tamanho));

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("uploadId", id);
        corpo.put("url", url);
        corpo.put("metodo", "PUT");
        corpo.put("contentType", UrlsPreassinadas.TIPO_XLSX);
        corpo.put("expiraEmSegundos", S3UrlsPreassinadas.VALIDADE.toSeconds());
        return json(201, corpo);
    }

    private APIGatewayV2HTTPResponse comDono(APIGatewayV2HTTPEvent evento, String id,
            java.util.function.Function<RegistroCalibracao, APIGatewayV2HTTPResponse> rota) {
        String sub = sub(evento);
        if (sub == null) {
            return problema(401, "Não autenticado", "Token sem identificação de usuário.");
        }
        if (id == null || !ID.matcher(id).matches()) {
            return problema(400, "Identificador inválido", "O identificador da calibração é inválido.");
        }
        return repositorio.buscar(id).filter(r -> r.sub().equals(sub)).map(rota)
                .orElseGet(() -> problema(404, "Não encontrada", "Calibração não encontrada."));
    }

    private APIGatewayV2HTTPResponse estado(RegistroCalibracao r) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("id", r.id());
        corpo.put("estado", r.estado());
        corpo.put("atualizadoEm", r.atualizadoEm().toString());
        if (r.sha256() != null) {
            corpo.put("sha256", r.sha256());
        }
        if (r.resultado() != null) {
            corpo.put("parecer", r.resultado().parecer());
            corpo.put("pontos", pontos(r.resultado()));
        }
        if (r.estado() == EstadoCalibracao.REJEITADA) {
            List<Map<String, Object>> erros = new ArrayList<>();
            r.erros().forEach(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("linha", e.linha());
                m.put("coluna", e.coluna());
                m.put("regra", e.regra());
                m.put("mensagem", e.mensagem());
                erros.add(m);
            });
            corpo.put("erros", erros);
        }
        if (r.estado() == EstadoCalibracao.EMITIDA) {
            corpo.put("sha256Pdf", r.sha256Pdf());
        }
        return json(200, corpo);
    }

    private static List<Map<String, Object>> pontos(ResultadoCalibracao resultado) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (ResultadoPonto p : resultado.pontos()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("caracteristica", p.ponto().caracteristica());
            m.put("valorReferencia", p.ponto().valorReferencia());
            m.put("tolSuperior", p.ponto().tolSuperior());
            m.put("tolInferior", p.ponto().tolInferior());
            m.put("leituras", p.ponto().leituras());
            m.put("media", p.media());
            m.put("erroSistematico", p.erroSistematico());
            m.put("lea", p.ponto().lea());
            m.put("status", p.status());
            lista.add(m);
        }
        return lista;
    }

    private APIGatewayV2HTTPResponse pdf(RegistroCalibracao r) {
        if (r.estado() != EstadoCalibracao.EMITIDA) {
            return problema(409, "Certificado indisponível", "O certificado ainda não foi emitido.");
        }
        return json(200, Map.of("url", urls.urlCertificado(r.id()),
                "expiraEmSegundos", S3UrlsPreassinadas.VALIDADE.toSeconds()));
    }

    /** Rota pública: sem nome, sem {@code sub}. */
    private APIGatewayV2HTTPResponse verificar(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) {
            return problema(404, "Não encontrado", "Certificado não encontrado.");
        }
        return repositorio.buscarPorToken(token)
                .filter(r -> r.estado() == EstadoCalibracao.EMITIDA)
                .map(r -> {
                    Map<String, Object> corpo = new LinkedHashMap<>();
                    corpo.put("id", r.id());
                    corpo.put("emitidoEm", r.atualizadoEm().toString());
                    corpo.put("parecer", r.resultado().parecer());
                    corpo.put("sha256Pdf", r.sha256Pdf());
                    return json(200, corpo);
                })
                .orElseGet(() -> problema(404, "Não encontrado", "Certificado não encontrado."));
    }

    private static String sub(APIGatewayV2HTTPEvent evento) {
        var contexto = evento.getRequestContext();
        if (contexto == null || contexto.getAuthorizer() == null || contexto.getAuthorizer().getJwt() == null
                || contexto.getAuthorizer().getJwt().getClaims() == null) {
            return null;
        }
        String sub = contexto.getAuthorizer().getJwt().getClaims().get("sub");
        return sub == null || sub.isBlank() ? null : sub;
    }

    private static String corpo(APIGatewayV2HTTPEvent evento) {
        String corpo = evento.getBody() == null ? "" : evento.getBody();
        return evento.getIsBase64Encoded()
                ? new String(Base64.getDecoder().decode(corpo), StandardCharsets.UTF_8) : corpo;
    }

    private static APIGatewayV2HTTPResponse json(int status, Object corpo) {
        return resposta(status, "application/json", Json.escrever(corpo));
    }

    private static APIGatewayV2HTTPResponse problema(int status, String titulo, String detalhe) {
        return resposta(status, PROBLEM_JSON, Json.escrever(problemaCorpo(status, titulo, detalhe)));
    }

    private static Map<String, Object> problemaCorpo(int status, String titulo, String detalhe) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("type", "about:blank");
        corpo.put("title", titulo);
        corpo.put("status", status);
        corpo.put("detail", detalhe);
        return corpo;
    }

    private static APIGatewayV2HTTPResponse resposta(int status, String tipo, String corpo) {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", tipo, "Cache-Control", "no-store"))
                .withBody(corpo)
                .build();
    }

    private static String exigir(UnaryOperator<String> ambiente, String nome) {
        String valor = ambiente.apply(nome);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Variável de ambiente " + nome + " não definida.");
        }
        return valor;
    }
}
