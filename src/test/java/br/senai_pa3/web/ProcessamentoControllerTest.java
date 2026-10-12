package br.senai_pa3.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import br.senai_pa3.aplicacao.NucleoConfig;
import br.senai_pa3.aplicacao.ProcessamentoService;

/**
 * Endpoint de processamento (T14) com o núcleo real, contra o oráculo
 * (docs/av03/oraculo-planilhas.md). Os erros de planilha ficam para a T15.
 */
@WebMvcTest(ProcessamentoController.class)
@Import({ ProcessamentoService.class, NucleoConfig.class })
class ProcessamentoControllerTest {

    private static final String ROTA = "/api/calibracoes/processamento";

    @Autowired
    private MockMvc mvc;

    @Test
    void okAprovado_parecerEPrimeiroPontoCampoACampo() throws Exception {
        enviar("ok-aprovado.xlsx")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parecer").value("APROVADO"))
                .andExpect(jsonPath("$.pontos.length()").value(3))
                .andExpect(jsonPath("$.pontos[0].caracteristica").value("Diâmetro eixo"))
                .andExpect(jsonPath("$.pontos[0].leituras[0]").value(4.13))
                .andExpect(jsonPath("$.pontos[0].leituras[1]").value(4.14))
                .andExpect(jsonPath("$.pontos[0].valorReferencia").value(4.12))
                .andExpect(jsonPath("$.pontos[0].media").value(4.135))
                .andExpect(jsonPath("$.pontos[0].erroSistematico").value(0.015))
                .andExpect(jsonPath("$.pontos[0].lea").value(0.03))
                .andExpect(jsonPath("$.pontos[0].status").value("DENTRO_DA_TOLERANCIA"));
    }

    @Test
    void numerosSaemComoNumeroJsonComCincoCasas() throws Exception {
        enviar("ok-aprovado.xlsx")
                .andExpect(jsonPath("$.pontos[0].media").isNumber())
                // Sem aspas: número, não texto. Os zeros à direita mostram a escala 5.
                .andExpect(content().string(containsString("\"media\":4.13500")))
                .andExpect(content().string(containsString("\"erroSistematico\":0.01500")));
    }

    @Test
    void okArredondamento_cincoCasasMostramOErroQueReprova() throws Exception {
        enviar("ok-arredondamento.xlsx")
                .andExpect(jsonPath("$.parecer").value("REPROVADO"))
                .andExpect(jsonPath("$.pontos[0].media").value(0.06045))
                .andExpect(jsonPath("$.pontos[0].erroSistematico").value(0.00045))
                .andExpect(jsonPath("$.pontos[0].status").value("FORA_DA_TOLERANCIA"))
                .andExpect(jsonPath("$.pontos[1].erroSistematico").value(-0.00045))
                .andExpect(jsonPath("$.pontos[1].status").value("FORA_DA_TOLERANCIA"));
    }

    @Test
    void okTresLeituras_dizimaArredondadaSoNaSaida() throws Exception {
        enviar("ok-tres-leituras.xlsx")
                .andExpect(jsonPath("$.parecer").value("REPROVADO"))
                .andExpect(jsonPath("$.pontos[0].leituras.length()").value(3))
                .andExpect(jsonPath("$.pontos[0].media").value(10.01667))
                .andExpect(jsonPath("$.pontos[1].media").value(1.00333))
                .andExpect(jsonPath("$.pontos[1].erroSistematico").value(0.00333))
                .andExpect(jsonPath("$.pontos[1].status").value("FORA_DA_TOLERANCIA"));
    }

    private ResultActions enviar(String arquivo) throws Exception {
        return mvc.perform(multipart(ROTA).file(planilha(arquivo)));
    }

    private MockMultipartFile planilha(String arquivo) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/planilhas/" + arquivo)) {
            return new MockMultipartFile("arquivo", arquivo,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", in.readAllBytes());
        }
    }
}
