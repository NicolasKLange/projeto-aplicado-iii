package br.senai_pa3.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * Escritor mínimo de JSON para as respostas da {@code fn-api}: evita uma biblioteca só para serializar
 * mapas e listas. Aceita {@code Map}, {@code Collection}, {@code String}, {@code Enum}, {@code Number},
 * {@code Boolean} e {@code null}. {@code BigDecimal} sai como texto em precisão completa, para não
 * perder a escala dos valores metrológicos.
 */
final class Json {

    private Json() {
    }

    static String escrever(Object valor) {
        StringBuilder sb = new StringBuilder();
        escrever(valor, sb);
        return sb.toString();
    }

    private static void escrever(Object valor, StringBuilder sb) {
        if (valor == null) {
            sb.append("null");
        } else if (valor instanceof BigDecimal d) {
            texto(d.toPlainString(), sb);
        } else if (valor instanceof Number || valor instanceof Boolean) {
            sb.append(valor);
        } else if (valor instanceof Map<?, ?> mapa) {
            sb.append('{');
            boolean primeiro = true;
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                if (!primeiro) {
                    sb.append(',');
                }
                primeiro = false;
                texto(e.getKey().toString(), sb);
                sb.append(':');
                escrever(e.getValue(), sb);
            }
            sb.append('}');
        } else if (valor instanceof Collection<?> colecao) {
            sb.append('[');
            boolean primeiro = true;
            for (Object o : colecao) {
                if (!primeiro) {
                    sb.append(',');
                }
                primeiro = false;
                escrever(o, sb);
            }
            sb.append(']');
        } else {
            texto(valor.toString(), sb);
        }
    }

    private static void texto(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
