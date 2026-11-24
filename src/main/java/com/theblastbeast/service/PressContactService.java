package com.theblastbeast.service;

import com.theblastbeast.domain.vo.PressContact;
import com.theblastbeast.domain.vo.PressContactList;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Lee el CSV interno de contactos de prensa e imagen.
 *
 * <p>El CSV sigue siendo la fuente de verdad en {@code docs/}. En desarrollo se
 * lee desde {@code ../docs/...} y en Docker se monta en {@code /app/docs} en
 * modo solo lectura. No se empaqueta en la imagen ni se publica como recurso
 * estático: solo se sirve a través de este servicio autenticado.
 */
@Slf4j
@Service
public class PressContactService {

    static final List<String> CSV_HEADERS = List.of(
            "categoria",
            "nombre",
            "tipo",
            "pais",
            "web",
            "enlace",
            "email",
            "contacto_nombre",
            "idioma",
            "tipo_de_material",
            "licencia_uso",
            "notas",
            "verificado");

    private final Path csvPath;

    public PressContactService(
            @Value("${blastbeast.press-contacts.csv-path:../docs/contactos-prensa-e-imagenes.csv}")
            String csvPath) {
        this.csvPath = Path.of(csvPath).toAbsolutePath().normalize();
    }

    public PressContactList list() {
        List<List<String>> rows = parse(readCsv());
        if (rows.isEmpty()) {
            throw unavailable("El CSV de contactos está vacío");
        }

        Map<String, Integer> index = headerIndex(rows.get(0));
        List<PressContact> contacts = rows.stream()
                .skip(1)
                .filter(row -> !isBlank(row))
                .map(row -> toContact(row, index))
                .toList();

        PressContactList result = new PressContactList();
        result.setRows(contacts);
        result.setTotal(contacts.size());
        result.setUpdatedAt(lastModified());
        log.debug("INIT - PressContactService -> list() - {} contactos desde {}", contacts.size(), csvPath);
        return result;
    }

    private String readCsv() {
        if (!Files.isRegularFile(csvPath)) {
            throw unavailable("No se encontró el CSV de contactos en " + csvPath);
        }
        try {
            return Files.readString(csvPath, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            BusinessException error = unavailable("No se pudo leer el CSV de contactos");
            error.initCause(exception);
            throw error;
        }
    }

    private OffsetDateTime lastModified() {
        try {
            Instant instant = Files.getLastModifiedTime(csvPath).toInstant();
            return instant.atOffset(ZoneOffset.UTC);
        } catch (IOException exception) {
            log.warn("No se pudo obtener la fecha del CSV de contactos: {}", exception.getMessage());
            return null;
        }
    }

    private Map<String, Integer> headerIndex(List<String> headerRow) {
        Map<String, Integer> index = new HashMap<>();
        for (int position = 0; position < headerRow.size(); position++) {
            index.put(headerRow.get(position).trim().toLowerCase(Locale.ROOT), position);
        }

        List<String> missing = CSV_HEADERS.stream()
                .filter(header -> !index.containsKey(header))
                .toList();
        if (!missing.isEmpty()) {
            throw unavailable("Faltan columnas en el CSV de contactos: " + String.join(", ", missing));
        }
        return index;
    }

    private PressContact toContact(List<String> row, Map<String, Integer> index) {
        PressContact contact = new PressContact();
        contact.setCategoria(value(row, index, "categoria"));
        contact.setNombre(value(row, index, "nombre"));
        contact.setTipo(value(row, index, "tipo"));
        contact.setPais(value(row, index, "pais"));
        contact.setWeb(value(row, index, "web"));
        contact.setEnlace(value(row, index, "enlace"));
        contact.setEmail(value(row, index, "email"));
        contact.setContactoNombre(value(row, index, "contacto_nombre"));
        contact.setIdioma(value(row, index, "idioma"));
        contact.setTipoDeMaterial(value(row, index, "tipo_de_material"));
        contact.setLicenciaUso(value(row, index, "licencia_uso"));
        contact.setNotas(value(row, index, "notas"));
        contact.setVerificado(value(row, index, "verificado"));
        return contact;
    }

    private String value(List<String> row, Map<String, Integer> index, String header) {
        int position = index.get(header);
        return position < row.size() ? row.get(position) : "";
    }

    private boolean isBlank(List<String> row) {
        return row.stream().allMatch(value -> value == null || value.isBlank());
    }

    /**
     * Parser CSV mínimo compatible con RFC 4180: comillas dobles, comas y
     * saltos de línea dentro de campos, y comillas escapadas como {@code ""}.
     */
    static List<List<String>> parse(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int start = content.startsWith("\uFEFF") ? 1 : 0;

        for (int position = start; position < content.length(); position++) {
            char current = content.charAt(position);
            if (quoted) {
                if (current == '"') {
                    if (position + 1 < content.length() && content.charAt(position + 1) == '"') {
                        field.append('"');
                        position++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(current);
                }
                continue;
            }

            switch (current) {
                case '"' -> quoted = true;
                case ',' -> {
                    row.add(field.toString());
                    field.setLength(0);
                }
                case '\n' -> {
                    row.add(field.toString());
                    field.setLength(0);
                    rows.add(row);
                    row = new ArrayList<>();
                }
                case '\r' -> {
                    // Ignorar: los saltos CRLF se cierran con el \n.
                }
                default -> field.append(current);
            }
        }

        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private BusinessException unavailable(String detail) {
        return new BusinessException(ErrorCode.PRESS_CONTACTS_UNAVAILABLE, detail);
    }
}
