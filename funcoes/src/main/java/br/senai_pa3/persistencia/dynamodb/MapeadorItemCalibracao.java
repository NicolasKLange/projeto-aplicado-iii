package br.senai_pa3.persistencia.dynamodb;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.senai_pa3.dominio.PontoEnsaio;
import br.senai_pa3.dominio.ResultadoCalibracao;
import br.senai_pa3.dominio.ResultadoPonto;
import br.senai_pa3.ingestao.ErroValidacao;
import br.senai_pa3.persistencia.EstadoCalibracao;
import br.senai_pa3.persistencia.RegistroCalibracao;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Converte {@link RegistroCalibracao} no item {@code CAL#<id>/META} da tabela
 * {@code calibracoes} (docs/av04/modelo-dados.md §1) e de volta.
 * <p>
 * Valores metrológicos vão como string ({@code toPlainString}), não como {@code N}: o
 * DynamoDB normaliza zeros à direita ({@code 10.0000000000} viraria {@code 10}) e a escala
 * 10 da média se perderia. Nunca passa por {@code double}.
 * <p>
 * 🔒 Só os atributos do modelo são escritos; não há atributo para nome de arquivo, conteúdo
 * bruto nem nome do usuário. Erros e média de resultado são derivados, não duplicados.
 */
public final class MapeadorItemCalibracao {

    static final String PREFIXO_CAL = "CAL#";
    static final String PREFIXO_USER = "USER#";
    static final String SK_META = "META";

    private MapeadorItemCalibracao() {
    }

    public static Map<String, AttributeValue> paraItem(RegistroCalibracao r) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s(PREFIXO_CAL + r.id()));
        item.put("SK", s(SK_META));
        item.put("sub", s(r.sub()));
        item.put("estado", s(r.estado().name()));
        item.put("atualizadoEm", s(r.atualizadoEm().toString()));
        item.put("GSI2PK", s(PREFIXO_USER + r.sub()));
        item.put("GSI2SK", s(r.atualizadoEm().toString()));
        if (r.sha256() != null) {
            item.put("sha256", s(r.sha256()));
            item.put("tamanhoBytes", AttributeValue.fromN(Long.toString(r.tamanhoBytes())));
        }
        if (r.resultado() != null) {
            item.put("resultado", resultado(r.resultado()));
        }
        if (!r.erros().isEmpty()) {
            item.put("erros", erros(r.erros()));
        }
        if (r.idOriginal() != null) {
            item.put("idOriginal", s(r.idOriginal()));
        }
        if (r.token() != null) {
            item.put("token", s(r.token()));
            item.put("sha256Pdf", s(r.sha256Pdf()));
        }
        return item;
    }

    public static RegistroCalibracao deItem(Map<String, AttributeValue> item) {
        String pk = texto(item, "PK");
        if (!pk.startsWith(PREFIXO_CAL)) {
            throw new IllegalArgumentException("Item sem chave CAL#.");
        }
        List<ErroValidacao> erros = item.containsKey("erros")
                ? item.get("erros").l().stream().map(v -> erro(v.m())).toList()
                : List.of();
        return new RegistroCalibracao(
                pk.substring(PREFIXO_CAL.length()),
                texto(item, "sub"),
                EstadoCalibracao.valueOf(texto(item, "estado")),
                opcional(item, "sha256"),
                item.containsKey("tamanhoBytes") ? Long.valueOf(item.get("tamanhoBytes").n()) : null,
                item.containsKey("resultado") ? resultado(item.get("resultado").m()) : null,
                erros,
                opcional(item, "idOriginal"),
                opcional(item, "token"),
                opcional(item, "sha256Pdf"),
                Instant.parse(texto(item, "atualizadoEm")));
    }

    static AttributeValue resultado(ResultadoCalibracao resultado) {
        List<AttributeValue> pontos = new ArrayList<>();
        for (ResultadoPonto rp : resultado.pontos()) {
            PontoEnsaio p = rp.ponto();
            Map<String, AttributeValue> m = new HashMap<>();
            m.put("caracteristica", s(p.caracteristica()));
            m.put("valorReferencia", decimal(p.valorReferencia()));
            m.put("tolSuperior", decimal(p.tolSuperior()));
            m.put("tolInferior", decimal(p.tolInferior()));
            m.put("leituras", AttributeValue.fromL(p.leituras().stream().map(MapeadorItemCalibracao::decimal).toList()));
            m.put("media", decimal(rp.media()));
            pontos.add(AttributeValue.fromM(m));
        }
        return AttributeValue.fromM(Map.of("pontos", AttributeValue.fromL(pontos)));
    }

    private static ResultadoCalibracao resultado(Map<String, AttributeValue> m) {
        List<ResultadoPonto> pontos = m.get("pontos").l().stream().map(v -> {
            Map<String, AttributeValue> p = v.m();
            List<BigDecimal> leituras = p.get("leituras").l().stream().map(l -> new BigDecimal(l.s())).toList();
            PontoEnsaio ponto = new PontoEnsaio(texto(p, "caracteristica"), new BigDecimal(texto(p, "valorReferencia")),
                    new BigDecimal(texto(p, "tolSuperior")), new BigDecimal(texto(p, "tolInferior")), leituras);
            return new ResultadoPonto(ponto, new BigDecimal(texto(p, "media")));
        }).toList();
        return new ResultadoCalibracao(pontos);
    }

    static AttributeValue erros(List<ErroValidacao> erros) {
        return AttributeValue.fromL(erros.stream().map(MapeadorItemCalibracao::erro).toList());
    }

    private static AttributeValue erro(ErroValidacao e) {
        Map<String, AttributeValue> m = new HashMap<>();
        if (e.linha() != null) {
            m.put("linha", AttributeValue.fromN(e.linha().toString()));
        }
        if (e.coluna() != null) {
            m.put("coluna", s(e.coluna()));
        }
        m.put("regra", s(e.regra()));
        m.put("mensagem", s(e.mensagem()));
        return AttributeValue.fromM(m);
    }

    private static ErroValidacao erro(Map<String, AttributeValue> m) {
        return new ErroValidacao(
                m.containsKey("linha") ? Integer.valueOf(m.get("linha").n()) : null,
                opcional(m, "coluna"), texto(m, "regra"), texto(m, "mensagem"));
    }

    private static AttributeValue s(String v) {
        return AttributeValue.fromS(v);
    }

    private static AttributeValue decimal(BigDecimal v) {
        return s(v.toPlainString());
    }

    private static String texto(Map<String, AttributeValue> m, String chave) {
        AttributeValue v = m.get(chave);
        if (v == null || v.s() == null) {
            throw new IllegalArgumentException("Atributo obrigatório ausente: " + chave);
        }
        return v.s();
    }

    private static String opcional(Map<String, AttributeValue> m, String chave) {
        AttributeValue v = m.get(chave);
        return v == null ? null : v.s();
    }
}
