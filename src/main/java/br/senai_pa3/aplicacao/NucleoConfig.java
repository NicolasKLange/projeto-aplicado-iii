package br.senai_pa3.aplicacao;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.senai_pa3.dominio.CalculadoraMetrologica;
import br.senai_pa3.ingestao.LeitorPlanilha;
import br.senai_pa3.ingestao.LeitorPlanilhaPoi;

/**
 * Registra o núcleo como beans. As classes de {@code dominio} e {@code ingestao} não levam
 * anotação do Spring (D-012): quem as liga ao contêiner é esta configuração.
 * <p>
 * Os dois são singletons sem estado: o {@link LeitorPlanilhaPoi} cria o {@code DataFormatter}
 * a cada chamada justamente para poder ser compartilhado entre requisições.
 */
@Configuration
public class NucleoConfig {

    @Bean
    LeitorPlanilha leitorPlanilha() {
        return new LeitorPlanilhaPoi();
    }

    @Bean
    CalculadoraMetrologica calculadoraMetrologica() {
        return new CalculadoraMetrologica();
    }
}
