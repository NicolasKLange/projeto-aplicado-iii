package br.senai_pa3.certificado;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import br.senai_pa3.dominio.Parecer;
import br.senai_pa3.dominio.ResultadoPonto;

/**
 * Monta o certificado em PDF (layout simplificado, divergência de PV-14). Só desenha: o cálculo vem
 * pronto do núcleo. Números em 5 casas só na exibição; as leituras saem como foram lidas.
 * <p>
 * 🔒 Não registra nada em log e não recebe nome de arquivo.
 */
public class GeradorCertificado {

    static final String RODAPE =
            "Documento gerado por PoC acadêmica (UniSENAI, Projeto Aplicado III) — sem validade metrológica";

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final int CASAS_EXIBICAO = 5;

    private static final float MARGEM = 40f;
    private static final float LINHA = 12f;
    private static final float TAMANHO = 9f;
    private static final float QR_LADO = 90f;

    private static final PDType1Font NORMAL = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font NEGRITO = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    // Colunas: característica, leituras, referência, média, erro, LEA, status (somam 515 = A4 - 2 margens).
    private static final String[] TITULOS =
            {"Característica", "Leituras", "Referência", "Média", "Erro", "LEA", "Status"};
    private static final float[] LARGURAS = {70f, 115f, 60f, 50f, 50f, 40f, 130f};

    public byte[] gerar(DadosCertificado d) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            doc.getDocumentInformation().setTitle("Certificado de calibração " + d.id());
            doc.getDocumentInformation().setProducer("PoC acadêmica - Projeto Aplicado III");
            new Desenho(doc, d).desenhar();
            doc.save(saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar o PDF do certificado.", e);
        }
    }

    /** Estado de desenho: página corrente, posição vertical e quebra de página. */
    private static final class Desenho {
        private final PDDocument doc;
        private final DadosCertificado d;
        private final PDRectangle formato = PDRectangle.A4;
        private PDPageContentStream cs;
        private float y;

        Desenho(PDDocument doc, DadosCertificado d) {
            this.doc = doc;
            this.d = d;
        }

        void desenhar() throws IOException {
            novaPagina();
            try {
                cabecalho();
                tabela();
                parecer();
                verificacao();
            } finally {
                cs.close();
            }
        }

        private void cabecalho() throws IOException {
            texto(NEGRITO, 16f, MARGEM, "Certificado de Calibração");
            y -= 22f;
            linha("Identificador: " + d.id());
            linha("Emitido em: " + DATA_HORA.format(d.emitidoEm().atZone(FUSO)) + " (America/Sao_Paulo)");
            linha("Gerado por: " + d.geradoPor());
            linha("SHA-256 da planilha de origem:");
            linha(d.sha256Planilha());
            y -= 8f;
        }

        private void tabela() throws IOException {
            linhaTabela(TITULOS, NEGRITO);
            for (ResultadoPonto r : d.resultado().pontos()) {
                String leituras = r.ponto().leituras().stream()
                        .map(BigDecimal::toPlainString).collect(Collectors.joining("; "));
                String[] celulas = {
                    r.ponto().caracteristica(), leituras,
                    exibir(r.ponto().valorReferencia()), exibir(r.media()), exibir(r.erroSistematico()),
                    exibir(r.ponto().lea()), r.status().name()};
                linhaTabela(celulas, NORMAL);
            }
            y -= 10f;
        }

        private void parecer() throws IOException {
            garantirEspaco(40f);
            boolean aprovado = d.resultado().parecer() == Parecer.APROVADO;
            float altura = 30f;
            // Verde-escuro para APROVADO, vermelho-escuro para REPROVADO; o texto branco também legível em P&B.
            cs.setNonStrokingColor(aprovado ? 0.10f : 0.70f, aprovado ? 0.45f : 0.10f, 0.15f);
            cs.addRect(MARGEM, y - altura + 8f, formato.getWidth() - 2 * MARGEM, altura);
            cs.fill();
            cs.setNonStrokingColor(1f, 1f, 1f);
            texto(NEGRITO, 14f, MARGEM + 10f, "PARECER: " + d.resultado().parecer().name(), y - 12f);
            cs.setNonStrokingColor(0f, 0f, 0f);
            y -= altura + 16f;
        }

        private void verificacao() throws IOException {
            garantirEspaco(QR_LADO + 20f);
            texto(NEGRITO, 10f, MARGEM, "Verificação de autenticidade");
            y -= 14f;
            float topo = y;
            linha("Token: " + d.token());
            // A URL pode passar da largura útil; quebra em pedaços (não é palavra com espaços).
            for (String parte : quebrar(d.urlVerificacao(), formato.getWidth() - 2 * MARGEM - QR_LADO - 20f, NORMAL)) {
                linha(parte);
            }
            qr(formato.getWidth() - MARGEM - QR_LADO, topo - QR_LADO + 8f);
            y = Math.min(y, topo - QR_LADO) - 8f;
        }

        /** Desenha a matriz do QR Code como retângulos: dispensa o módulo {@code javase} do ZXing. */
        private void qr(float x, float baseY) throws IOException {
            BitMatrix m;
            try {
                m = new QRCodeWriter().encode(d.urlVerificacao(), BarcodeFormat.QR_CODE, 0, 0,
                        Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                                EncodeHintType.MARGIN, 1,
                                EncodeHintType.CHARACTER_SET, "UTF-8"));
            } catch (WriterException e) {
                throw new IllegalStateException("URL de verificação não cabe em um QR Code.", e);
            }
            float modulo = QR_LADO / m.getWidth();
            cs.setNonStrokingColor(0f, 0f, 0f);
            for (int linha = 0; linha < m.getHeight(); linha++) {
                for (int col = 0; col < m.getWidth(); col++) {
                    if (m.get(col, linha)) {
                        cs.addRect(x + col * modulo, baseY + (m.getHeight() - 1 - linha) * modulo, modulo, modulo);
                    }
                }
            }
            cs.fill();
        }

        private void linhaTabela(String[] celulas, PDType1Font fonte) throws IOException {
            List<List<String>> quebradas = new ArrayList<>();
            int linhas = 1;
            for (int i = 0; i < celulas.length; i++) {
                List<String> q = quebrar(celulas[i], LARGURAS[i] - 6f, fonte);
                quebradas.add(q);
                linhas = Math.max(linhas, q.size());
            }
            float altura = linhas * LINHA + 4f;
            garantirEspaco(altura);

            float x = MARGEM;
            cs.setStrokingColor(0.6f, 0.6f, 0.6f);
            for (int i = 0; i < celulas.length; i++) {
                cs.addRect(x, y - altura + LINHA - 2f, LARGURAS[i], altura);
                cs.stroke();
                float ty = y - 2f;
                for (String parte : quebradas.get(i)) {
                    texto(fonte, TAMANHO, x + 3f, parte, ty);
                    ty -= LINHA;
                }
                x += LARGURAS[i];
            }
            y -= altura;
        }

        private void linha(String s) throws IOException {
            texto(NORMAL, 10f, MARGEM, s);
            y -= LINHA + 1f;
        }

        private void texto(PDType1Font fonte, float tam, float x, String s) throws IOException {
            texto(fonte, tam, x, s, y);
        }

        private void texto(PDType1Font fonte, float tam, float x, String s, float ty) throws IOException {
            cs.beginText();
            cs.setFont(fonte, tam);
            cs.newLineAtOffset(x, ty);
            cs.showText(codificavel(s, fonte));
            cs.endText();
        }

        private void garantirEspaco(float altura) throws IOException {
            if (y - altura < MARGEM + 20f) {
                cs.close();
                novaPagina();
            }
        }

        private void novaPagina() throws IOException {
            PDPage pagina = new PDPage(formato);
            doc.addPage(pagina);
            cs = new PDPageContentStream(doc, pagina);
            y = formato.getHeight() - MARGEM;
            cs.setNonStrokingColor(0.35f, 0.35f, 0.35f);
            texto(NORMAL, 7.5f, MARGEM, RODAPE, MARGEM - 14f);
            cs.setNonStrokingColor(0f, 0f, 0f);
        }
    }

    /** Casas só na exibição; o valor original não é alterado. */
    private static String exibir(BigDecimal v) {
        return v.setScale(CASAS_EXIBICAO, RoundingMode.HALF_EVEN).toPlainString();
    }

    /**
     * Quebra {@code s} em linhas que cabem em {@code largura}, por palavras e, se uma palavra sozinha
     * não couber (hash, URL), por caracteres.
     */
    private static List<String> quebrar(String s, float largura, PDType1Font fonte) throws IOException {
        List<String> linhas = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        for (char c : codificavel(s, fonte).toCharArray()) {
            String candidata = atual.toString() + c;
            if (atual.length() > 0 && largura(candidata, fonte) > largura) {
                int espaco = atual.lastIndexOf(" ");
                if (c != ' ' && espaco > 0) {
                    linhas.add(atual.substring(0, espaco));
                    atual = new StringBuilder(atual.substring(espaco + 1));
                } else {
                    linhas.add(atual.toString());
                    atual = new StringBuilder();
                }
            }
            if (!(atual.length() == 0 && c == ' ')) {
                atual.append(c);
            }
        }
        if (atual.length() > 0 || linhas.isEmpty()) {
            linhas.add(atual.toString());
        }
        return linhas;
    }

    private static float largura(String s, PDType1Font fonte) throws IOException {
        return fonte.getStringWidth(s) / 1000f * TAMANHO;
    }

    /**
     * As fontes padrão do PDF cobrem só WinAnsi; um nome de usuário com caractere fora dele
     * ({@code showText} lançaria exceção) vira {@code ?} em vez de derrubar a emissão.
     */
    private static String codificavel(String s, PDType1Font fonte) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            try {
                fonte.encode(String.valueOf(c));
                b.append(c);
            } catch (IOException | IllegalArgumentException e) {
                b.append('?');
            }
        }
        return b.toString();
    }
}
