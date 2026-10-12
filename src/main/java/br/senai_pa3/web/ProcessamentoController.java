package br.senai_pa3.web;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.senai_pa3.aplicacao.ProcessamentoService;

/**
 * Casca HTTP do processamento: recebe o upload e devolve o DTO. Erros de
 * planilha viram
 * resposta no advice da T15.
 */
@RestController
@RequestMapping("/api/calibracoes")
public class ProcessamentoController {

    private final ProcessamentoService servico;

    public ProcessamentoController(ProcessamentoService servico) {
        this.servico = servico;
    }

    @PostMapping(path = "/processamento", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResultadoCalibracaoDto processar(@RequestParam("arquivo") MultipartFile arquivo) throws IOException {
        try (InputStream entrada = arquivo.getInputStream()) {
            return ResultadoCalibracaoDto.de(servico.processar(entrada));
        }
    }
}
