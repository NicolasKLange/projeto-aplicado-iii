package br.senai_pa3.certificado;

import java.time.Instant;
import java.util.Objects;

import br.senai_pa3.dominio.ResultadoCalibracao;

/**
 * Tudo o que o certificado mostra além do cálculo. {@code geradoPor} é o nome de exibição do usuário
 * autenticado: existe só no PDF, nunca nas tabelas (que guardam o {@code sub}).
 *
 * @param id             identificador da calibração
 * @param emitidoEm      instante da emissão (exibido em America/Sao_Paulo)
 * @param sha256Planilha hash da planilha de origem
 * @param geradoPor      nome de exibição de quem emitiu
 * @param resultado      pontos calculados
 * @param token          token de verificação
 * @param urlVerificacao URL pública de verificação, a mesma que vai no QR Code
 */
public record DadosCertificado(
        String id,
        Instant emitidoEm,
        String sha256Planilha,
        String geradoPor,
        ResultadoCalibracao resultado,
        String token,
        String urlVerificacao) {

    public DadosCertificado {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(emitidoEm, "emitidoEm");
        Objects.requireNonNull(sha256Planilha, "sha256Planilha");
        Objects.requireNonNull(geradoPor, "geradoPor");
        Objects.requireNonNull(resultado, "resultado");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(urlVerificacao, "urlVerificacao");
    }
}
