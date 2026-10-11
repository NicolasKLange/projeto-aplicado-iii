package br.senai_pa3.ingestao;

import java.io.InputStream;
import java.util.List;

import br.senai_pa3.dominio.PontoEnsaio;

/**
 * Porta de entrada: transforma a planilha do contrato (docs/av03/contrato-planilha.md) nos
 * pontos de ensaio. Trocar o formato (POI, CSV, exportação do Calypso) não toca no cálculo.
 */
public interface LeitorPlanilha {

    /** Os pontos da aba Medicoes, na ordem das linhas. */
    List<PontoEnsaio> ler(InputStream entrada);
}
