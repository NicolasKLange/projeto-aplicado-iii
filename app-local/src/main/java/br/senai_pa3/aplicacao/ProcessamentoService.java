package br.senai_pa3.aplicacao;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.senai_pa3.dominio.CalculadoraMetrologica;
import br.senai_pa3.dominio.PontoEnsaio;
import br.senai_pa3.dominio.ResultadoCalibracao;
import br.senai_pa3.ingestao.LeitorPlanilha;
import br.senai_pa3.ingestao.Medidor;
import br.senai_pa3.ingestao.PlanilhaInvalidaException;

/**
 * Caso de uso do processamento: planilha → pontos → resultado. Não conhece HTTP; recebe o
 * conteúdo como {@link InputStream}, o mesmo que a {@code fn-ingestao} vai entregar.
 * <p>
 * Cada planilha deixa no log o SHA-256 e o tamanho, nunca o conteúdo nem o nome do arquivo (LGPD).
 */
@Service
public class ProcessamentoService {

    private static final Logger log = LoggerFactory.getLogger(ProcessamentoService.class);

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
        Medidor medidor = new Medidor(entrada);
        try {
            List<PontoEnsaio> pontos = leitor.ler(medidor);
            return calculadora.calcular(pontos);
        } finally {
            registrar(medidor);
        }
    }

    private static void registrar(Medidor medidor) {
        try {
            medidor.consumirRestante();
            log.info("Planilha recebida: sha256={} bytes={}", medidor.sha256(), medidor.bytes());
        } catch (IOException e) {
            // Não mascara o erro do processamento, que já está subindo.
            log.warn("Planilha recebida: hash indisponível, a entrada falhou após {} bytes", medidor.bytes());
        }
    }
}
