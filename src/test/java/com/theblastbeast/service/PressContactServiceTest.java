package com.theblastbeast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.theblastbeast.domain.vo.PressContact;
import com.theblastbeast.domain.vo.PressContactList;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PressContactServiceTest {

    private static final String HEADER = """
            "categoria","nombre","tipo","pais","web","enlace","email","contacto_nombre","idioma","tipo_de_material","licencia_uso","notas","verificado"
            """;

    @TempDir
    Path tempDir;

    @Test
    void listaContactosDelCsv() throws IOException {
        Path csv = writeCsv("\uFEFF" + HEADER + """
                "Sellos","Frontiers Music","Sello discográfico","Italia","https://www.frontiers.it/","https://www.frontiers.it/press/","","","Inglés / Italiano","Fotos de prensa en ZIP, portadas y logos","Uso editorial con crédito","Directorio abierto, con comas y ""comillas"".","2026-11-19"
                "Festivales","Leyendas del Rock","Festival","España","https://leyendasdelrock.com/","","","","Español","Fotos y material de prensa","Uso editorial con crédito","","2026-11-20"
                """);

        PressContactList result = new PressContactService(csv.toString()).list();

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getUpdatedAt()).isNotNull();

        PressContact first = result.getRows().get(0);
        assertThat(first.getCategoria()).isEqualTo("Sellos");
        assertThat(first.getNombre()).isEqualTo("Frontiers Music");
        assertThat(first.getPais()).isEqualTo("Italia");
        assertThat(first.getContactoNombre()).isEmpty();
        assertThat(first.getTipoDeMaterial()).isEqualTo("Fotos de prensa en ZIP, portadas y logos");
        assertThat(first.getLicenciaUso()).isEqualTo("Uso editorial con crédito");
        assertThat(first.getNotas()).isEqualTo("Directorio abierto, con comas y \"comillas\".");
        assertThat(first.getVerificado()).isEqualTo("2026-11-19");
    }

    @Test
    void ignoraFilasVaciasYCompletaColumnasAusentes() throws IOException {
        Path csv = writeCsv(HEADER + """

                "Sellos","Avispa Music"
                """);

        PressContactList result = new PressContactService(csv.toString()).list();

        assertThat(result.getTotal()).isEqualTo(1);
        PressContact contact = result.getRows().get(0);
        assertThat(contact.getNombre()).isEqualTo("Avispa Music");
        assertThat(contact.getWeb()).isEmpty();
        assertThat(contact.getVerificado()).isEmpty();
    }

    @Test
    void fallaSiNoExisteElCsv() {
        Path missing = tempDir.resolve("no-existe.csv");

        assertThatThrownBy(() -> new PressContactService(missing.toString()).list())
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PRESS_CONTACTS_UNAVAILABLE));
    }

    @Test
    void fallaSiFaltanColumnasObligatorias() throws IOException {
        Path csv = writeCsv("""
                "categoria","nombre","tipo","pais","web","enlace","contacto_nombre","idioma","tipo_de_material","licencia_uso","notas","verificado"
                "Sellos","Frontiers Music","Sello","Italia","https://www.frontiers.it/","","","Inglés","Fotos","Uso editorial","","2026-11-19"
                """);

        assertThatThrownBy(() -> new PressContactService(csv.toString()).list())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("email");
    }

    @Test
    void fallaSiElCsvEstaVacio() throws IOException {
        Path csv = writeCsv("");

        assertThatThrownBy(() -> new PressContactService(csv.toString()).list())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("vacío");
    }

    @Test
    void parseaSaltosDeLineaDentroDeCamposEntrecomillados() {
        List<List<String>> rows = PressContactService.parse(
                "\uFEFF\"a\",\"b\"\r\n\"uno\",\"línea 1\nlínea 2\"\r\n");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsExactly("a", "b");
        assertThat(rows.get(1)).containsExactly("uno", "línea 1\nlínea 2");
    }

    private Path writeCsv(String content) throws IOException {
        Path csv = tempDir.resolve("contactos.csv");
        Files.writeString(csv, content, StandardCharsets.UTF_8);
        return csv;
    }
}
