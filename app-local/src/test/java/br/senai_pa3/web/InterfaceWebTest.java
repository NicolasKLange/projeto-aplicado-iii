package br.senai_pa3.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import br.senai_pa3.aplicacao.NucleoConfig;
import br.senai_pa3.aplicacao.ProcessamentoService;

/**
 * Interface web (T16): o Boot serve {@code static/} e usa o {@code index.html} como página de
 * {@code /}. O comportamento da tela (fetch, tabela, erros) é provado pelos prints da T18.
 */
@WebMvcTest(ProcessamentoController.class)
@Import({ ProcessamentoService.class, NucleoConfig.class })
class InterfaceWebTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void indexHtml_servidoComOFormulario() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("id=\"form-planilha\"")));
    }

    @Test
    void raiz_encaminhaParaOIndex() throws Exception {
        // Quebra se algum controller passar a mapear "/": a welcome page deixa de valer.
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }
}
