package com.theblastbeast.util;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Utilidades mínimas para construir XML sin dependencias externas.
 *
 * <p>El sitemap y los feeds se generan a mano porque su estructura es fija y
 * pequeña; así el contrato JSON de la API no se contamina con recursos XML.
 */
public final class XmlSupport {

    private static final DateTimeFormatter RFC_1123 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    private XmlSupport() {
    }

    /** Escapa texto y valores de atributo para XML 1.0. */
    public static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> escaped.append(character);
            }
        }
        return escaped.toString();
    }

    /**
     * Envuelve contenido en CDATA. Si el texto contiene {@code ]]>}, lo parte en
     * dos secciones CDATA para que el XML siga siendo válido.
     */
    public static String cdata(String value) {
        if (value == null || value.isEmpty()) {
            return "<![CDATA[]]>";
        }
        return "<![CDATA[" + value.replace("]]>", "]]]]><![CDATA[>") + "]]>";
    }

    /** Fecha ISO-8601 en UTC para {@code <lastmod>}. */
    public static String lastmod(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC).toInstant().toString();
    }

    /** Fecha RFC 1123 en UTC para {@code <pubDate>} y {@code <lastBuildDate>}. */
    public static String rfc1123(OffsetDateTime value) {
        if (value == null) {
            return "";
        }
        return value.withOffsetSameInstant(ZoneOffset.UTC).format(RFC_1123);
    }
}
