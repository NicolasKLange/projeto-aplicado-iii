package br.senai_pa3.web;

import java.util.List;

import br.senai_pa3.dominio.ResultadoCalibracao;

/**
 * Corpo da resposta do processamento. É o contrato da API: mudar um record do domínio não muda
 * o JSON sem passar por aqui.
 *
 * @param parecer nome exato do enum ({@code APROVADO} / {@code REPROVADO})
 * @param pontos  na ordem da planilha
 */
public record ResultadoCalibracaoDto(String parecer, List<PontoDto> pontos) {

    static ResultadoCalibracaoDto de(ResultadoCalibracao resultado) {
        return new ResultadoCalibracaoDto(
                resultado.parecer().name(),
                resultado.pontos().stream().map(PontoDto::de).toList());
    }
}
