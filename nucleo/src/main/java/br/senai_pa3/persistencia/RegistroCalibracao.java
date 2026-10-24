package br.senai_pa3.persistencia;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import br.senai_pa3.dominio.ResultadoCalibracao;
import br.senai_pa3.ingestao.ErroValidacao;

/**
 * Item de um envio, como a tela e o PDF o leem. Os campos opcionais são
 * {@code null} quando o estado não os tem; o construtor recusa combinações
 * incoerentes, então não existe {@code REJEITADA} com resultado nem
 * {@code PROCESSADA} sem ele.
 * <p>
 * 🔒 Não há campo para nome do arquivo, conteúdo bruto nem nome do usuário: o dono
 * é só o {@code sub} do Cognito (opaco).
 *
 * @param id             identificador do envio (o mesmo do upload e da calibração)
 * @param sub            {@code sub} do Cognito de quem enviou
 * @param estado         estado atual
 * @param sha256         hash da planilha, 64 hex minúsculos; {@code null} em PROCESSANDO
 * @param tamanhoBytes   tamanho da planilha; {@code null} em PROCESSANDO
 * @param resultado      cálculo; só em PROCESSADA e EMITIDA
 * @param erros          erros de validação; só em REJEITADA (nunca vazia ali)
 * @param idOriginal     envio dono do hash; só em DUPLICADA
 * @param token          token de verificação do certificado; só em EMITIDA
 * @param sha256Pdf      hash do PDF emitido; só em EMITIDA
 * @param atualizadoEm   instante da última transição (UTC)
 */
public record RegistroCalibracao(
        String id,
        String sub,
        EstadoCalibracao estado,
        String sha256,
        Long tamanhoBytes,
        ResultadoCalibracao resultado,
        List<ErroValidacao> erros,
        String idOriginal,
        String token,
        String sha256Pdf,
        Instant atualizadoEm) {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public RegistroCalibracao {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sub, "sub");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(atualizadoEm, "atualizadoEm");
        erros = erros == null ? List.of() : List.copyOf(erros);
        if (sha256 != null && !SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("sha256 deve ter 64 dígitos hexadecimais minúsculos.");
        }
        if ((estado == EstadoCalibracao.PROCESSANDO) != (sha256 == null)) {
            throw new IllegalArgumentException("Só PROCESSANDO fica sem sha256.");
        }
        if ((sha256 == null) != (tamanhoBytes == null)) {
            throw new IllegalArgumentException("sha256 e tamanhoBytes andam juntos.");
        }
        boolean calculada = estado == EstadoCalibracao.PROCESSADA || estado == EstadoCalibracao.EMITIDA;
        if (calculada != (resultado != null)) {
            throw new IllegalArgumentException("Resultado existe só em PROCESSADA e EMITIDA.");
        }
        if ((estado == EstadoCalibracao.REJEITADA) != !erros.isEmpty()) {
            throw new IllegalArgumentException("Erros existem só em REJEITADA, e lá nunca vazios.");
        }
        if ((estado == EstadoCalibracao.DUPLICADA) != (idOriginal != null)) {
            throw new IllegalArgumentException("idOriginal existe só em DUPLICADA.");
        }
        boolean emitida = estado == EstadoCalibracao.EMITIDA;
        if (emitida != (token != null) || emitida != (sha256Pdf != null)) {
            throw new IllegalArgumentException("Token e sha256Pdf existem só em EMITIDA.");
        }
    }
}
