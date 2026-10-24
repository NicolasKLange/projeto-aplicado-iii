package br.senai_pa3.ingestao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Caracteriza o POI diante de planilhas salvas como OOXML Strict (R03): o {@link XSSFWorkbook}
 * recusa o arquivo com {@link POIXMLException}.
 */
class PoiStrictTest {

    private static final String MENSAGEM_POI = "Strict OOXML isn't currently supported";

    /** Planilha real, fora do Git ({@code src/test/resources/local/}): sem ela, o teste é pulado. */
    private static final Path STRICT_REAL = Path.of("src/test/resources/local/strict-real.xlsx");

    @Test
    void xssfWorkbookRecusaStrictReal() throws IOException {
        assumeTrue(Files.exists(STRICT_REAL), "arquivo local ausente (não versionado)");
        InputStream in = Files.newInputStream(STRICT_REAL);

        assertThatThrownBy(() -> abre(in))
                .isInstanceOf(POIXMLException.class)
                .hasMessageContaining(MENSAGEM_POI);
    }

    @Test
    void xssfWorkbookRecusaStrictSintetico() {
        // ok-aprovado salvo como Strict no Excel desktop (oráculo, cenários inválidos).
        InputStream in = getClass().getResourceAsStream("/planilhas/inv-R03-strict.xlsx");
        assertThat(in).as("inv-R03-strict.xlsx no classpath").isNotNull();

        assertThatThrownBy(() -> abre(in))
                .isInstanceOf(POIXMLException.class)
                .hasMessageContaining(MENSAGEM_POI);
    }

    private static void abre(InputStream entrada) throws Exception {
        try (InputStream in = entrada; XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            // não deve chegar aqui
        }
    }
}
