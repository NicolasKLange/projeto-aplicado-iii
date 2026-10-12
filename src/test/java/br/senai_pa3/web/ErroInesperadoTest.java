package br.senai_pa3.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.senai_pa3.aplicacao.ProcessamentoService;

/**
 * 500 do {@link TratamentoErros}: classe separada porque o serviço é mock, e o resto da API
 * é testado com o núcleo real.
 */
@WebMvcTest(ProcessamentoController.class)
class ErroInesperadoTest {

    private static final String UUID = "[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}";
    private static final String CAMINHO = "/srv/app/segredo/config.yml";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProcessamentoService servico;

    @Test
    void falhaInesperada_500ComIdESemDetalheTecnico() throws Exception {
        given(servico.processar(any(InputStream.class)))
                .willThrow(new RuntimeException("Falha ao abrir " + CAMINHO));

        mvc.perform(multipart("/api/calibracoes/processamento")
                .file(new MockMultipartFile("arquivo", new byte[] { 1 })))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.id").value(matchesPattern(UUID)))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("at br."))))
                .andExpect(content().string(not(containsString(CAMINHO))));
    }
}
