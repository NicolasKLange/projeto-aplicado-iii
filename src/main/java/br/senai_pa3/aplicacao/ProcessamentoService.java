package br.senai_pa3.aplicacao;

import java.io.InputStream;
import java.util.List;

import org.springframework.stereotype.Service;

import br.senai_pa3.dominio.CalculadoraMetrologica;
import br.senai_pa3.dominio.PontoEnsaio;
import br.senai_pa3.dominio.ResultadoCalibracao;
import br.senai_pa3.ingestao.LeitorPlanilha;
import br.senai_pa3.ingestao.PlanilhaInvalidaException;

/**
 * Caso de uso do processamento: planilha → pontos → resultado. Não conhece HTTP; recebe o
 * conteúdo como {@link InputStream}, o mesmo que a {@code fn-ingestao} vai entregar.
 */
@Service
public class ProcessamentoService {

    private final LeitorPlanilha leitor;
    private final CalculadoraMetrologica calculadora;

    public ProcessamentoService(LeitorPlanilha leitor, CalculadoraMetrologica calculadora) {
        this.leitor = leitor;
        this.calculadora = calculadora;
    }

    /**
     * Lê e calcula. Quem chama fecha a {@code entrada}.
     *
     * @throws PlanilhaInvalidaException se a planilha não cumpre o contrato (R01–R15)
     */
    public ResultadoCalibracao processar(InputStream entrada) {
        List<PontoEnsaio> pontos = leitor.ler(entrada);
        return calculadora.calcular(pontos);
    }
}
