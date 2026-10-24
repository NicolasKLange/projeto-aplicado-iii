package br.senai_pa3.ingestao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Um teste por arquivo {@code inv-*} da T06 (docs/av03/oraculo-planilhas.md),
 * com o código
 * da regra no nome. Confere regra, linha e coluna de cada erro.
 */
class LeitorPlanilhaPoiInvalidosTest {

    private final LeitorPlanilha leitor = new LeitorPlanilhaPoi();

    @Test
    void r01_maiorQue5MB() {
        // 5 MB + 1 byte: a R01 vem antes de olhar o conteúdo.
        byte[] grande = new byte[5 * 1024 * 1024 + 1];

        assertThat(erros(new ByteArrayInputStream(grande))).containsExactly(ErroValidacao.geral("R01",
                "O arquivo excede o limite de 5 MB."));
    }

    @Test
    void r01_exatamente5MBPassaParaAsProximasRegras() {
        // No limite, a R01 não se aplica: zeros não são xlsx, então cai na R02.
        byte[] limite = new byte[5 * 1024 * 1024];

        assertThat(erros(new ByteArrayInputStream(limite))).containsExactly(ErroValidacao.geral("R02",
                MENSAGEM_R02));
    }

    @Test
    void r02_csvComExtensaoXlsx() throws IOException {
        List<ErroValidacao> erros = erros("inv-R02-nao-xlsx.xlsx");

        assertThat(erros).containsExactly(ErroValidacao.geral("R02", MENSAGEM_R02));
    }

    @Test
    void r02_arquivoVazio() {
        // R02, não R08: a R08 é uma planilha válida sem linhas; 0 bytes nem é xlsx.
        List<ErroValidacao> erros = erros(new ByteArrayInputStream(new byte[0]));

        assertThat(erros).containsExactly(ErroValidacao.geral("R02", MENSAGEM_R02));
    }

    @Test
    void r02_xlsxCortado() throws IOException {
        // Começa com PK (passa no FileMagic), mas o zip termina no meio da primeira
        // entrada.
        byte[] cortado = Arrays.copyOf(bytes("ok-aprovado.xlsx"), 500);

        List<ErroValidacao> erros = erros(new ByteArrayInputStream(cortado));

        assertThat(erros).containsExactly(ErroValidacao.geral("R02", MENSAGEM_R02));
    }

    @Test
    void zipSecureFile_limiteDeEntradaDe50MB() {
        // O bloco static roda ao carregar o leitor (campo desta classe). O caminho da rejeição
        // não é testado: exigiria gerar uma entrada de mais de 50 MB. Ver D-013.
        assertThat(ZipSecureFile.getMaxEntrySize()).isEqualTo(LeitorPlanilhaPoi.MAXIMO_ENTRADA_ZIP)
                .isEqualTo(50L * 1024 * 1024);
    }

    // --- estrutura: um erro só, sem linha ---

    @Test
    void r03_strict() throws IOException {
        assertThat(erros("inv-R03-strict.xlsx")).containsExactly(ErroValidacao.geral("R03",
                "O arquivo está em formato OOXML Strict. Salve como 'Pasta de Trabalho do Excel (.xlsx)'."));
    }

    @Test
    void r04_semAbaMedicoes() throws IOException {
        assertThat(erros("inv-R04-sem-aba.xlsx")).containsExactly(ErroValidacao.geral("R04",
                "A planilha fornecida não contém a aba Medicoes."));
    }

    @Test
    void r05_semToleranciaSuperior() throws IOException {
        assertThat(erros("inv-R05-sem-tolsup.xlsx")).containsExactly(new ErroValidacao(null,
                "ToleranciaSuperior", "R05",
                "O cabeçalho ToleranciaSuperior não consta na planilha fornecida."));
    }

    @Test
    void r06_umaLeitura() throws IOException {
        assertThat(erros("inv-R06-uma-leitura.xlsx")).containsExactly(ErroValidacao.geral("R06",
                "A planilha fornecida não contém duas séries de medições com cabeçalho Leitura* "
                        + "(Leitura1, Leitura2)."));
    }

    @Test
    void r07_colunaDesvio() throws IOException {
        assertThat(erros("inv-R07-desvio.xlsx")).containsExactly(ErroValidacao.geral("R07",
                "A planilha contém a coluna Desvio, que não é utilizada neste cálculo. "
                        + "Remova a coluna e carregue novamente o arquivo."));
    }

    @Test
    void r08_soCabecalho() throws IOException {
        assertThat(erros("inv-R08-vazia.xlsx")).containsExactly(ErroValidacao.geral("R08",
                "A planilha fornecida não contém dados."));
    }

    @Test
    void r09_dezMilEUmaLinhas() throws IOException {
        assertThat(erros(planilhaComLinhas(10_001))).containsExactly(ErroValidacao.geral("R09",
                "A planilha excede o limite de 10 mil linhas."));
    }

    @Test
    void r09_dezMilLinhasCabemNoLimite() throws IOException {
        assertThat(leitor.ler(planilhaComLinhas(10_000))).hasSize(10_000);
    }

    @Test
    void r09_linhasSoFormatadasNaoContam() throws IOException {
        // 50 pontos (linhas 2 a 51) e formatação até a linha 20 000: getLastRowNum() =
        // 19 999.
        InputStream planilha = planilha(linhasValidas(50), 52, 20_000);

        assertThat(leitor.ler(planilha)).hasSize(50);
    }

    @Test
    void r09_registroAlemDoTetoGrosseiro() throws IOException {
        // Um ponto e uma linha formatada solta além do teto de 100 000 linhas
        // registradas.
        InputStream planilha = planilha(linhasValidas(1), 100_002, 100_002);

        assertThat(erros(planilha)).containsExactly(ErroValidacao.geral("R09",
                "A planilha excede o limite de 10 mil linhas."));
    }

    // --- linhas: todos os erros coletados ---

    @Test
    void r10_leituraVazia() throws IOException {
        assertThat(erros("inv-R10-obrigatorio.xlsx")).containsExactly(new ErroValidacao(3, "Leitura2", "R10",
                "Linha 3, coluna Leitura2: valor obrigatório ausente."));
    }

    @Test
    void r11_textoNaLeitura() throws IOException {
        assertThat(erros("inv-R11-texto.xlsx")).containsExactly(new ErroValidacao(2, "Leitura1", "R11",
                "Linha 2, coluna Leitura1: 'abc' não é um número válido."));
    }

    @Test
    void r11_formulaNaLeitura() throws IOException {
        // Fórmula não é aceita (contrato, §3), nem com valor numérico em cache.
        InputStream planilha = planilha(List.<Object[]>of(
                new Object[] { "Diâmetro eixo", 4.12, new Formula("1+1"), 4.14, 0.03, -0.03 }));

        assertThat(erros(planilha)).containsExactly(new ErroValidacao(2, "Leitura1", "R11",
                "Linha 2, coluna Leitura1: contém fórmula; digite o valor medido."));
    }

    @Test
    void r12_caracteristicaRepetida() throws IOException {
        assertThat(erros("inv-R12-repetida.xlsx")).containsExactly(new ErroValidacao(5, "Caracteristica", "R12",
                "Característica 'Diâmetro eixo' repetida nas linhas 2 e 5."));
    }

    @Test
    void r13_toleranciaSuperiorZero() throws IOException {
        assertThat(erros("inv-R13-tolsup-zero.xlsx")).containsExactly(new ErroValidacao(4, "ToleranciaSuperior",
                "R13", "Linha 4 ('Largura base'): a tolerância superior precisa ser maior que zero."));
    }

    @Test
    void r14_toleranciaAssimetrica() throws IOException {
        assertThat(erros("inv-R14-assimetrica.xlsx")).containsExactly(new ErroValidacao(2, "ToleranciaInferior",
                "R14", "Linha 2 ('Diâmetro eixo'): tolerância +0.05/-0.02 fora do escopo do MVP — "
                        + "aceitas: bilateral simétrica ou unilateral."));
    }

    @Test
    void r15_leituraNegativaEmUnilateral() throws IOException {
        assertThat(erros("inv-R15-negativa-unilateral.xlsx")).containsExactly(new ErroValidacao(5, "Leitura1",
                "R15", "Linha 5 ('Planicidade base'): leitura negativa em característica unilateral — "
                        + "verifique a medição."));
    }

    @Test
    void r15_duasLeiturasNegativasNaMesmaLinha_umErroPorLeitura() throws IOException {
        InputStream planilha = planilha(List.<Object[]>of(
                new Object[] { "Planicidade base", 0, -0.002, -0.001, 0.05, 0 }));
        String mensagem = "Linha 2 ('Planicidade base'): leitura negativa em característica unilateral — "
                + "verifique a medição.";

        assertThat(erros(planilha)).containsExactly(
                new ErroValidacao(2, "Leitura1", "R15", mensagem),
                new ErroValidacao(2, "Leitura2", "R15", mensagem));
    }

    @Test
    void multiplosErros_r10R11R14_umPorLinha() throws IOException {
        assertThat(erros("inv-multiplos-erros.xlsx")).containsExactly(
                new ErroValidacao(2, "Leitura2", "R10", "Linha 2, coluna Leitura2: valor obrigatório ausente."),
                new ErroValidacao(3, "Leitura1", "R11", "Linha 3, coluna Leitura1: 'n/a' não é um número válido."),
                new ErroValidacao(4, "ToleranciaInferior", "R14",
                        "Linha 4 ('Largura base'): tolerância +0.007/-0.002 fora do escopo do MVP — "
                                + "aceitas: bilateral simétrica ou unilateral."));
    }

    // --- apoio ---

    /**
     * Cabeçalho do contrato e {@code n} linhas válidas, com característica única.
     */
    private static InputStream planilhaComLinhas(int n) throws IOException {
        return planilha(linhasValidas(n));
    }

    private static List<Object[]> linhasValidas(int n) {
        return IntStream.rangeClosed(1, n)
                .mapToObj(i -> new Object[] { "Ponto " + i, 10.0, 10.01, 10.02, 0.03, -0.03 })
                .toList();
    }

    /**
     * Célula de fórmula no gerador de planilhas (sem o "=" inicial, como no POI).
     */
    private record Formula(String expressao) {
    }

    /**
     * Aba Medicoes com o cabeçalho do contrato (duas leituras) e as linhas dadas:
     * texto vira
     * célula de texto, número vira célula numérica, {@link Formula} vira célula de
     * fórmula.
     */
    private static InputStream planilha(List<Object[]> linhas) throws IOException {
        return planilha(linhas, 0, -1);
    }

    /**
     * Como {@link #planilha(List)}, mais linhas só formatadas (célula em branco com
     * estilo) da
     * linha {@code primeira} à {@code ultima}, no padrão do Excel. Elas entram no
     * XML e contam no
     * {@code getLastRowNum()}, como as que o Excel grava quando se formata uma
     * coluna inteira.
     */
    private static InputStream planilha(List<Object[]> linhas, int primeira, int ultima) throws IOException {
        try (XSSFWorkbook pasta = new XSSFWorkbook(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            Sheet aba = pasta.createSheet("Medicoes");
            String[] nomes = { "Caracteristica", "ValorReferencia", "Leitura1", "Leitura2",
                    "ToleranciaSuperior", "ToleranciaInferior" };
            Row cabecalho = aba.createRow(0);
            for (int c = 0; c < nomes.length; c++) {
                cabecalho.createCell(c).setCellValue(nomes[c]);
            }
            for (int i = 0; i < linhas.size(); i++) {
                Row linha = aba.createRow(i + 1);
                Object[] valores = linhas.get(i);
                for (int c = 0; c < valores.length; c++) {
                    if (valores[c] instanceof Number numero) {
                        linha.createCell(c).setCellValue(numero.doubleValue());
                    } else if (valores[c] instanceof Formula formula) {
                        linha.createCell(c).setCellFormula(formula.expressao());
                    } else {
                        linha.createCell(c).setCellValue((String) valores[c]);
                    }
                }
            }
            // Grava o resultado das fórmulas em cache, como o Excel faz ao salvar.
            pasta.getCreationHelper().createFormulaEvaluator().evaluateAll();
            CellStyle estilo = pasta.createCellStyle();
            estilo.setDataFormat(pasta.createDataFormat().getFormat("0.0000"));
            for (int n = primeira; n <= ultima; n++) {
                aba.createRow(n - 1).createCell(2).setCellStyle(estilo);
            }
            pasta.write(saida);
            return new ByteArrayInputStream(saida.toByteArray());
        }
    }

    private static final String MENSAGEM_R02 = "O arquivo não está em formato xlsx. Salve como 'Pasta de Trabalho do Excel (.xlsx)'.";

    private List<ErroValidacao> erros(String arquivo) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/planilhas/" + arquivo)) {
            assertThat(in).as(arquivo + " no classpath").isNotNull();
            return erros(in);
        }
    }

    private List<ErroValidacao> erros(InputStream in) {
        PlanilhaInvalidaException e = catchThrowableOfType(PlanilhaInvalidaException.class, () -> leitor.ler(in));
        assertThat(e).as("PlanilhaInvalidaException").isNotNull();
        return e.erros();
    }

    private byte[] bytes(String arquivo) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/planilhas/" + arquivo)) {
            assertThat(in).as(arquivo + " no classpath").isNotNull();
            return in.readAllBytes();
        }
    }
}
