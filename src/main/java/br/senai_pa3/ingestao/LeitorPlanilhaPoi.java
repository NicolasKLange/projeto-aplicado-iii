package br.senai_pa3.ingestao;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import br.senai_pa3.dominio.PontoEnsaio;

/**
 * Adaptador da {@link LeitorPlanilha} para .xlsx Transitional, com Apache POI.
 * <p>
 * Por enquanto, um erro de arquivo ou de estrutura lança {@link IllegalArgumentException} com a
 * mensagem do contrato; a {@code PlanilhaInvalidaException} com a lista de erros entra na T13.
 */
public class LeitorPlanilhaPoi implements LeitorPlanilha {

    private static final String ABA = "Medicoes";
    private static final String CARACTERISTICA = "Caracteristica";
    private static final String VALOR_REFERENCIA = "ValorReferencia";
    private static final String TOL_SUPERIOR = "ToleranciaSuperior";
    private static final String TOL_INFERIOR = "ToleranciaInferior";
    /** Leitura1..N; recusa Leituras, LeituraX, "Leitura obs". */
    private static final Pattern LEITURA = Pattern.compile("Leitura\\d+");
    private static final String DESVIO = "Desvio";
    private static final List<String> OBRIGATORIOS =
            List.of(CARACTERISTICA, VALOR_REFERENCIA, TOL_SUPERIOR, TOL_INFERIOR);

    private final DataFormatter formatador = new DataFormatter();

    @Override
    public List<PontoEnsaio> ler(InputStream entrada) {
        try (OPCPackage pacote = OPCPackage.open(entrada)) {
            // O XSSFWorkbook também recusa o Strict (POIXMLException, ver PoiStrictTest),
            // mas checar antes dá a mensagem do contrato sem depender do texto do POI.
            if (pacote.isStrictOoxmlFormat()) {
                throw new IllegalArgumentException("O arquivo está em formato OOXML Strict. "
                        + "Salve como 'Pasta de Trabalho do Excel (.xlsx)'.");
            }
            try (XSSFWorkbook pasta = new XSSFWorkbook(pacote)) {
                Sheet aba = pasta.getSheet(ABA);
                if (aba == null) {
                    throw new IllegalArgumentException("A planilha fornecida não contém a aba Medicoes.");
                }
                Cabecalho cabecalho = lerCabecalho(aba.getRow(0));
                return lerLinhas(aba, cabecalho);
            }
        } catch (InvalidFormatException e) {
            throw new IllegalArgumentException("O arquivo não está em formato xlsx. "
                    + "Salve como 'Pasta de Trabalho do Excel (.xlsx)'.", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Índice de cada coluna pelo nome do cabeçalho; as leituras na ordem das colunas. */
    private record Cabecalho(Map<String, Integer> colunas, List<Integer> leituras) {

        int coluna(String nome) {
            return colunas.get(nome);
        }
    }

    private Cabecalho lerCabecalho(Row linha) {
        Map<String, Integer> colunas = new HashMap<>();
        List<Integer> leituras = new ArrayList<>();
        if (linha != null) {
            for (Cell celula : linha) {
                String nome = formatador.formatCellValue(celula).trim();
                colunas.put(nome, celula.getColumnIndex());
                if (LEITURA.matcher(nome).matches()) {
                    leituras.add(celula.getColumnIndex());
                }
            }
        }
        for (String obrigatorio : OBRIGATORIOS) {
            if (!colunas.containsKey(obrigatorio)) {
                throw new IllegalArgumentException(
                        "O cabeçalho %s não consta na planilha fornecida.".formatted(obrigatorio));
            }
        }
        if (leituras.size() < 2) {
            throw new IllegalArgumentException("A planilha fornecida não contém duas séries de medições "
                    + "com cabeçalho Leitura* (Leitura1, Leitura2).");
        }
        if (colunas.containsKey(DESVIO)) {
            throw new IllegalArgumentException("A planilha contém a coluna Desvio, que não é utilizada "
                    + "neste cálculo. Remova a coluna e carregue novamente o arquivo.");
        }
        return new Cabecalho(colunas, List.copyOf(leituras));
    }

    private List<PontoEnsaio> lerLinhas(Sheet aba, Cabecalho cabecalho) {
        List<PontoEnsaio> pontos = new ArrayList<>();
        for (int i = 1; i <= aba.getLastRowNum(); i++) {
            Row linha = aba.getRow(i);
            if (linha == null) {
                continue;
            }
            List<BigDecimal> leituras = cabecalho.leituras().stream()
                    .map(coluna -> numero(linha, coluna))
                    .toList();
            pontos.add(new PontoEnsaio(
                    formatador.formatCellValue(linha.getCell(cabecalho.coluna(CARACTERISTICA))).trim(),
                    numero(linha, cabecalho.coluna(VALOR_REFERENCIA)),
                    numero(linha, cabecalho.coluna(TOL_SUPERIOR)),
                    numero(linha, cabecalho.coluna(TOL_INFERIOR)),
                    leituras));
        }
        if (pontos.isEmpty()) {
            throw new IllegalArgumentException("A planilha fornecida não contém dados.");
        }
        return pontos;
    }

    /**
     * O Excel guarda o double mais próximo (4,14 vira 4.1399999999999997). {@code BigDecimal.valueOf}
     * passa pelo {@code Double.toString}, que devolve o decimal mais curto que identifica esse
     * double: 4.14. {@code new BigDecimal(double)} daria a expansão binária inteira.
     */
    private static BigDecimal numero(Row linha, int coluna) {
        Cell celula = linha.getCell(coluna);
        if (celula == null || celula.getCellType() != CellType.NUMERIC) {
            // R10 e R11 com linha e coluna entram na T13.
            throw new IllegalArgumentException("Linha %d, coluna %d: número esperado."
                    .formatted(linha.getRowNum() + 1, coluna + 1));
        }
        return BigDecimal.valueOf(celula.getNumericCellValue());
    }
}
