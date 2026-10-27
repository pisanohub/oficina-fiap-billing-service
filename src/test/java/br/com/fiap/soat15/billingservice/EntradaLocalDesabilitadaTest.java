package br.com.fiap.soat15.billingservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EntradaLocalDesabilitadaTest {
    @Autowired MockMvc mvc;

    @Test
    void rotaDeDesenvolvimentoNaoExisteSemPerfilLocal() throws Exception {
        mvc.perform(get("/internal/orcamentos/11111111-1111-4111-8111-111111111111")).andExpect(status().isNotFound());
        mvc.perform(post("/internal/orcamentos").contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
    }
}
