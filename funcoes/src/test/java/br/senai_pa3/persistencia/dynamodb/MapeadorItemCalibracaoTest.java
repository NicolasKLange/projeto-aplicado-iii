package br.senai_pa3.persistencia.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import br.senai_pa3.dominio.PontoEnsaio;
import br.senai_pa3.dominio.ResultadoCalibracao;
import br.senai_pa3.dominio.ResultadoPonto;
import br.senai_pa3.ingestao.ErroValidacao;
import br.senai_pa3.persistencia.EstadoCalibracao;
import br.senai_pa3.persistencia.RegistroCalibracao;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/** Ida e volta registro → item → registro, sem DynamoDB. Só dados sintéticos. */
class MapeadorItemCalibracaoTest {

    private static final String SHA256 = "a".repeat(64);
    private static final Instant AGORA = Instant.parse("2026-10-25T12:00:00Z");

    private static RegistroCalibracao processada(BigDecimal media) {
        PontoEnsaio ponto = new PontoEnsaio("P1", new BigDecimal("10"), new BigDecimal("0.05"),
                new BigDecimal("-0.05"), List.of(new BigDecimal("10.00"), new BigDecimal("10.00")));
        return new RegistroCalibracao("u-1", "sub-opaco-1", EstadoCalibracao.PROCESSADA, SHA256, 2048L,
                new ResultadoCalibracao(List.of(new ResultadoPonto(ponto, media))), null, null, null, null, AGORA);
    }

    private static BigDecimal mediaDoPrimeiroPonto(RegistroCalibracao r) {
        return r.resultado().pontos().get(0).media();
    }

    private static RegistroCalibracao idaEVolta(RegistroCalibracao original) {
        Map<String, AttributeValue> item = MapeadorItemCalibracao.paraItem(original);
        return MapeadorItemCalibracao.deItem(item);
    }

    @Test
    void processada_mediaDezComZerosPreservaEscalaDez() {
        BigDecimal media = new BigDecimal("10.0000000000");
        assertThat(media.scale()).isEqualTo(10);

        BigDecimal lida = mediaDoPrimeiroPonto(idaEVolta(processada(media)));

        assertThat(lida.scale()).isEqualTo(10);
        assertThat(lida).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(lida.compareTo(media)).isZero();
        assertThat(lida.toPlainString()).isEqualTo("10.0000000000");
    }

    @Test
    void processada_mediaComMaisDigitosQueDoubleSuportaVoltaExata() {
        // 18 dígitos significativos: um double colapsaria os últimos.
        BigDecimal media = new BigDecimal("12345678.9012345679");
        assertThat(media.scale()).isEqualTo(10);
        assertThat(media.doubleValue()).isEqualTo(new BigDecimal("12345678.9012345678").doubleValue());

        BigDecimal lida = mediaDoPrimeiroPonto(idaEVolta(processada(media)));

        assertThat(lida.compareTo(media)).isZero();
        assertThat(lida.compareTo(new BigDecimal("12345678.9012345678"))).isNotZero();
        assertThat(lida.scale()).isEqualTo(10);
    }

    @Test
    void processada_voltaIgualAoOriginal() {
        RegistroCalibracao original = processada(new BigDecimal("10.0000000000"));

        RegistroCalibracao lido = idaEVolta(original);

        assertThat(lido.id()).isEqualTo("u-1");
        assertThat(lido.sub()).isEqualTo("sub-opaco-1");
        assertThat(lido.estado()).isEqualTo(EstadoCalibracao.PROCESSADA);
        assertThat(lido.sha256()).isEqualTo(SHA256);
        assertThat(lido.tamanhoBytes()).isEqualTo(2048L);
        assertThat(lido.atualizadoEm()).isEqualTo(AGORA);
        // record.equals usa BigDecimal.equals (valor e escala): igualdade total só vale se a escala voltou.
        assertThat(lido).isEqualTo(original);
    }

    private static RegistroCalibracao registro(EstadoCalibracao estado, String sha, Long tam, ResultadoCalibracao res,
            List<ErroValidacao> erros, String idOriginal, String token, String sha256Pdf) {
        return new RegistroCalibracao("u-1", "sub-opaco-1", estado, sha, tam, res, erros, idOriginal, token, sha256Pdf,
                AGORA);
    }

    private static List<RegistroCalibracao> umDeCadaEstado() {
        ResultadoCalibracao res = processada(new BigDecimal("10.0000000000")).resultado();
        return List.of(
                registro(EstadoCalibracao.PROCESSANDO, null, null, null, null, null, null, null),
                registro(EstadoCalibracao.PROCESSADA, SHA256, 2048L, res, null, null, null, null),
                registro(EstadoCalibracao.REJEITADA, SHA256, 2048L, null,
                        List.of(new ErroValidacao(5, "Tol+", "R13", "Tolerância deve ser maior que zero."),
                                ErroValidacao.geral("R03", "Formato não suportado.")),
                        null, null, null),
                registro(EstadoCalibracao.DUPLICADA, SHA256, 2048L, null, null, "u-0", null, null),
                registro(EstadoCalibracao.EMITIDA, SHA256, 2048L, res, null, null, "tok-123", "b".repeat(64)));
    }

    @Test
    void cincoEstados_idaEVoltaIgualAoOriginal() {
        List<RegistroCalibracao> todos = umDeCadaEstado();
        assertThat(todos).extracting(RegistroCalibracao::estado).containsExactlyInAnyOrder(EstadoCalibracao.values());

        for (RegistroCalibracao original : todos) {
            assertThat(idaEVolta(original)).as(original.estado().name()).isEqualTo(original);
        }
    }

    @Test
    void rejeitada_guardaErrosComCamposNulosPreservados() {
        RegistroCalibracao lido = idaEVolta(umDeCadaEstado().get(2));

        assertThat(lido.erros()).containsExactly(
                new ErroValidacao(5, "Tol+", "R13", "Tolerância deve ser maior que zero."),
                new ErroValidacao(null, null, "R03", "Formato não suportado."));
        assertThat(lido.resultado()).isNull();
    }

    @Test
    void nenhumAtributoGuardaNomeDeArquivoOuConteudo() {
        Set<String> permitidos = Set.of("PK", "SK", "sub", "estado", "atualizadoEm", "GSI2PK", "GSI2SK", "sha256",
                "tamanhoBytes", "resultado", "erros", "idOriginal", "token", "sha256Pdf");
        Set<String> permitidosNoResultado = Set.of("pontos", "caracteristica", "valorReferencia", "tolSuperior",
                "tolInferior", "leituras", "media");
        Set<String> permitidosNoErro = Set.of("linha", "coluna", "regra", "mensagem");

        for (RegistroCalibracao r : umDeCadaEstado()) {
            Map<String, AttributeValue> item = MapeadorItemCalibracao.paraItem(r);
            assertThat(item.keySet()).as(r.estado().name()).isSubsetOf(permitidos);
            if (item.containsKey("resultado")) {
                AttributeValue res = item.get("resultado");
                assertThat(res.m().keySet()).isSubsetOf(permitidosNoResultado);
                res.m().get("pontos").l().forEach(p -> assertThat(p.m().keySet()).isSubsetOf(permitidosNoResultado));
            }
            if (item.containsKey("erros")) {
                item.get("erros").l().forEach(e -> assertThat(e.m().keySet()).isSubsetOf(permitidosNoErro));
            }
            // dono só pelo sub opaco; nenhum valor termina em extensão de planilha
            List<String> textos = new ArrayList<>();
            item.values().stream().map(AttributeValue::s).filter(java.util.Objects::nonNull).forEach(textos::add);
            assertThat(textos).noneMatch(t -> t.toLowerCase().matches(".*\\.(xlsx|xlsm|xls|pdf)$"));
        }
    }
}
