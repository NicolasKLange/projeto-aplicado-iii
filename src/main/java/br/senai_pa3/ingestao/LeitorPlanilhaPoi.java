package br.senai_pa3.ingestao;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.poifs.filesystem.FileMagic;
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
 * Erro de arquivo ou de estrutura (R02–R09) lança {@link PlanilhaInvalidaException} com um erro
 * só, porque sem a aba ou o cabeçalho não há o que ler. Os erros de linha (R10–R15) são todos
 * coletados e lançados juntos no fim.
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
    /** R09: linhas de dados não vazias, contadas no loop. */
    private static final int MAXIMO_LINHAS = 10_000;
    /**
     * Teto grosseiro para o getLastRowNum(), que conta toda linha registrada no XML, inclusive
     * as só formatadas. Bem acima da R09 para não recusar planilha com formatação sobrando.
     */
    private static final int TETO_LINHAS_REGISTRADAS = 100_000;
    private static final String MENSAGEM_R09 = "A planilha excede o limite de 10 mil linhas.";
    /** R01: tamanho máximo do arquivo, conferido antes do POI. */
    private static final int MAXIMO_BYTES = 5 * 1024 * 1024;
    private static final String MENSAGEM_R02 =
            "O arquivo não está em formato xlsx. Salve como 'Pasta de Trabalho do Excel (.xlsx)'.";
    /**
     * Limite de cada entrada descompactada do .xlsx (D-013). A maior planilha que o contrato
     * aceita (10 mil linhas, 5 leituras) tem a aba com ~4,2 MB de XML: 50 MB dá mais de 10× de folga.
     */
    static final long MAXIMO_ENTRADA_ZIP = 50L * 1024 * 1024;

    static {
        // O padrão do POI é 4 GB por entrada. Com a R01 (5 MB) e a taxa máxima padrão de 100×,
        // uma entrada ainda chegaria a ~500 MB de XML antes do DOM: o heap não aguenta.
        // ZipSecureFile é global na JVM; só o leitor usa o POI. Estourar o limite lança
        // IOException ("Zip bomb detected!"), que vira R02 no lerPacote.
        ZipSecureFile.setMaxEntrySize(MAXIMO_ENTRADA_ZIP);
    }

    @Override
    public List<PontoEnsaio> ler(InputStream entrada) {
        byte[] conteudo;
        try {
            // Lê no máximo um byte além do limite: basta para saber que passou, sem carregar o
            // resto. Falha aqui é de I/O (o upload caiu), não do arquivo: não vira erro de validação.
            conteudo = entrada.readNBytes(MAXIMO_BYTES + 1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (conteudo.length > MAXIMO_BYTES) {
            throw erroGeral("R01", "O arquivo excede o limite de 5 MB.");
        }
        // R02 pelo conteúdo, não pela extensão: um .xlsx começa com a assinatura do zip (PK).
        // Testar o vazio antes: o FileMagic lança EmptyFileException com 0 bytes.
        if (conteudo.length == 0 || FileMagic.valueOf(conteudo) != FileMagic.OOXML) {
            throw erroGeral("R02", MENSAGEM_R02);
        }
        return lerPacote(conteudo);
    }

    private List<PontoEnsaio> lerPacote(byte[] conteudo) {
        try (OPCPackage pacote = OPCPackage.open(new ByteArrayInputStream(conteudo))) {
            // O XSSFWorkbook também recusa o Strict (POIXMLException, ver PoiStrictTest),
            // mas checar antes dá a mensagem do contrato sem depender do texto do POI.
            if (pacote.isStrictOoxmlFormat()) {
                throw erroGeral("R03", "O arquivo está em formato OOXML Strict. "
                        + "Salve como 'Pasta de Trabalho do Excel (.xlsx)'.");
            }
            try (XSSFWorkbook pasta = new XSSFWorkbook(pacote)) {
                Sheet aba = pasta.getSheet(ABA);
                if (aba == null) {
                    throw erroGeral("R04", "A planilha fornecida não contém a aba Medicoes.");
                }
                // Teto antes de varrer as linhas. Não economiza memória: o XSSFWorkbook já
                // carregou a aba inteira (DOM). A R09 de verdade é contada no loop.
                if (aba.getLastRowNum() > TETO_LINHAS_REGISTRADAS) {
                    throw erroGeral("R09", MENSAGEM_R09);
                }
                // DataFormatter não é thread-safe: um por chamada, porque o leitor será singleton (T14).
                DataFormatter formatador = new DataFormatter();
                Cabecalho cabecalho = lerCabecalho(aba.getRow(0), formatador);
                return lerLinhas(aba, cabecalho, formatador);
            }
        } catch (InvalidFormatException e) {
            // Zip que não é pacote OOXML (sem [Content_Types].xml, por exemplo).
            throw erroGeral("R02", MENSAGEM_R02);
        } catch (IOException e) {
            // A leitura é em memória, sem I/O real: IOException aqui é zip corrompido, cortado
            // ("Truncated ZIP file") ou acima do limite do ZipSecureFile ("Zip bomb detected!").
            throw erroGeral("R02", MENSAGEM_R02);
        }
    }

    /** Erro de arquivo ou de estrutura: interrompe a leitura com um erro só. */
    private static PlanilhaInvalidaException erroGeral(String regra, String mensagem) {
        return new PlanilhaInvalidaException(List.of(ErroValidacao.geral(regra, mensagem)));
    }

    /** Índice de cada coluna pelo nome do cabeçalho; as leituras na ordem das colunas. */
    private record Cabecalho(Map<String, Integer> colunas, List<String> leituras) {

        int coluna(String nome) {
            return colunas.get(nome);
        }
    }

    private static Cabecalho lerCabecalho(Row linha, DataFormatter formatador) {
        Map<String, Integer> colunas = new HashMap<>();
        List<String> leituras = new ArrayList<>();
        if (linha != null) {
            for (Cell celula : linha) {
                String nome = formatador.formatCellValue(celula).trim();
                colunas.put(nome, celula.getColumnIndex());
                if (LEITURA.matcher(nome).matches()) {
                    leituras.add(nome);
                }
            }
        }
        for (String obrigatorio : OBRIGATORIOS) {
            if (!colunas.containsKey(obrigatorio)) {
                throw new PlanilhaInvalidaException(List.of(new ErroValidacao(null, obrigatorio, "R05",
                        "O cabeçalho %s não consta na planilha fornecida.".formatted(obrigatorio))));
            }
        }
        if (leituras.size() < 2) {
            throw erroGeral("R06", "A planilha fornecida não contém duas séries de medições "
                    + "com cabeçalho Leitura* (Leitura1, Leitura2).");
        }
        if (colunas.containsKey(DESVIO)) {
            throw erroGeral("R07", "A planilha contém a coluna Desvio, que não é utilizada "
                    + "neste cálculo. Remova a coluna e carregue novamente o arquivo.");
        }
        return new Cabecalho(colunas, List.copyOf(leituras));
    }

    /**
     * Valida todas as linhas e só lança no fim, com todos os erros (R10 a R15). Um ponto só é
     * criado de linha sem erro; o construtor do {@link PontoEnsaio} repete R13–R15 como defesa.
     */
    private static List<PontoEnsaio> lerLinhas(Sheet aba, Cabecalho cabecalho, DataFormatter formatador) {
        Coleta coleta = new Coleta(cabecalho, formatador);
        List<PontoEnsaio> pontos = new ArrayList<>();
        Map<String, Integer> primeiraLinha = new HashMap<>();
        int linhasDeDados = 0;
        for (int i = 1; i <= aba.getLastRowNum(); i++) {
            Row linha = aba.getRow(i);
            if (coleta.vazia(linha)) {
                continue;
            }
            // Só linha com conteúdo conta: formatação sobrando não reprova a planilha.
            if (++linhasDeDados > MAXIMO_LINHAS) {
                throw erroGeral("R09", MENSAGEM_R09);
            }
            int n = i + 1; // linha no padrão do Excel
            int errosAntes = coleta.erros.size();

            String caracteristica = coleta.texto(linha, n, CARACTERISTICA);
            BigDecimal referencia = coleta.numero(linha, n, VALOR_REFERENCIA);
            Map<String, BigDecimal> leituras = new LinkedHashMap<>();
            for (String nome : cabecalho.leituras()) {
                leituras.put(nome, coleta.numero(linha, n, nome));
            }
            BigDecimal tolSuperior = coleta.numero(linha, n, TOL_SUPERIOR);
            BigDecimal tolInferior = coleta.numero(linha, n, TOL_INFERIOR);
            // R13–R15 precisam de todos os valores: com célula ausente ou inválida, ficam para depois.
            boolean celulasValidas = coleta.erros.size() == errosAntes;

            if (caracteristica != null) {
                Integer anterior = primeiraLinha.putIfAbsent(caracteristica, n);
                if (anterior != null) {
                    coleta.erro(n, CARACTERISTICA, "R12", "Característica '%s' repetida nas linhas %d e %d."
                            .formatted(caracteristica, anterior, n));
                }
            }
            if (celulasValidas) {
                coleta.regrasDeTolerancia(n, caracteristica, tolSuperior, tolInferior, leituras);
            }
            if (coleta.erros.size() == errosAntes) {
                pontos.add(new PontoEnsaio(caracteristica, referencia, tolSuperior, tolInferior,
                        List.copyOf(leituras.values())));
            }
        }
        if (!coleta.erros.isEmpty()) {
            throw new PlanilhaInvalidaException(coleta.erros);
        }
        if (pontos.isEmpty()) {
            throw erroGeral("R08", "A planilha fornecida não contém dados.");
        }
        return pontos;
    }

    /** Lê as células de uma linha registrando os erros em vez de lançar. */
    private static final class Coleta {

        private final Cabecalho cabecalho;
        private final DataFormatter formatador;
        private final List<ErroValidacao> erros = new ArrayList<>();

        Coleta(Cabecalho cabecalho, DataFormatter formatador) {
            this.cabecalho = cabecalho;
            this.formatador = formatador;
        }

        void erro(int linha, String coluna, String regra, String mensagem) {
            erros.add(new ErroValidacao(linha, coluna, regra, mensagem));
        }

        /** Linha inexistente ou sem nada nas colunas do contrato (sobra de formatação no Excel). */
        boolean vazia(Row linha) {
            return linha == null || cabecalho.colunas().values().stream()
                    .allMatch(coluna -> celula(linha, coluna) == null);
        }

        /** R10 se vazia; senão o texto como aparece na célula. */
        String texto(Row linha, int n, String coluna) {
            Cell celula = celula(linha, cabecalho.coluna(coluna));
            if (celula == null) {
                obrigatorio(n, coluna);
                return null;
            }
            return formatador.formatCellValue(celula).trim();
        }

        /**
         * R10 se vazia, R11 se não for célula numérica. O Excel guarda o double mais próximo
         * (4,14 vira 4.1399999999999997); {@code BigDecimal.valueOf} passa pelo
         * {@code Double.toString}, que devolve o decimal mais curto desse double: 4.14.
         * {@code new BigDecimal(double)} daria a expansão binária inteira.
         */
        BigDecimal numero(Row linha, int n, String coluna) {
            Cell celula = celula(linha, cabecalho.coluna(coluna));
            if (celula == null) {
                obrigatorio(n, coluna);
                return null;
            }
            if (celula.getCellType() == CellType.FORMULA) {
                // Valor calculado não entra, nem em cache: pode estar desatualizado ou ausente.
                erro(n, coluna, "R11", "Linha %d, coluna %s: contém fórmula; digite o valor medido."
                        .formatted(n, coluna));
                return null;
            }
            if (celula.getCellType() != CellType.NUMERIC) {
                // Texto, booleano ou erro: o contrato pede número digitado.
                erro(n, coluna, "R11", "Linha %d, coluna %s: '%s' não é um número válido."
                        .formatted(n, coluna, formatador.formatCellValue(celula).trim()));
                return null;
            }
            return BigDecimal.valueOf(celula.getNumericCellValue());
        }

        void regrasDeTolerancia(int n, String nome, BigDecimal tolSuperior, BigDecimal tolInferior,
                Map<String, BigDecimal> leituras) {
            if (tolSuperior.signum() <= 0) {
                erro(n, TOL_SUPERIOR, "R13", "Linha %d ('%s'): a tolerância superior precisa ser maior que zero."
                        .formatted(n, nome));
                return;
            }
            boolean simetrica = tolInferior.compareTo(tolSuperior.negate()) == 0;
            boolean unilateral = tolInferior.signum() == 0;
            if (!simetrica && !unilateral) {
                erro(n, TOL_INFERIOR, "R14", ("Linha %d ('%s'): tolerância +%s/%s fora do escopo do MVP — "
                        + "aceitas: bilateral simétrica ou unilateral.")
                        .formatted(n, nome, tolSuperior.toPlainString(), tolInferior.toPlainString()));
                return;
            }
            if (unilateral) {
                // Um erro por leitura negativa: o operador corrige todas de uma vez.
                leituras.entrySet().stream()
                        .filter(leitura -> leitura.getValue().signum() < 0)
                        .forEach(leitura -> erro(n, leitura.getKey(), "R15", ("Linha %d ('%s'): leitura "
                                + "negativa em característica unilateral — verifique a medição.")
                                .formatted(n, nome)));
            }
        }

        private void obrigatorio(int n, String coluna) {
            erro(n, coluna, "R10", "Linha %d, coluna %s: valor obrigatório ausente.".formatted(n, coluna));
        }

        /** {@code null} para célula ausente, em branco ou só com espaços. */
        private Cell celula(Row linha, int coluna) {
            Cell celula = linha.getCell(coluna, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (celula == null || formatador.formatCellValue(celula).isBlank()) {
                return null;
            }
            return celula;
        }
    }
}
