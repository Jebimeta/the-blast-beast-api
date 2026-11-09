package com.theblastbeast.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.theblastbeast.domain.entity.Article;
import com.theblastbeast.domain.entity.ArticleStatus;
import com.theblastbeast.domain.entity.Category;
import com.theblastbeast.domain.entity.MediaAsset;
import com.theblastbeast.domain.entity.User;
import com.theblastbeast.domain.vo.MediaAssetResponse;
import com.theblastbeast.domain.vo.MediaPage;
import com.theblastbeast.domain.vo.MediaVariant;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@DisplayName("Redacción: medios")
class AdminMediaIT extends AbstractIntegrationTest {

    private User author;
    private Category noticias;

    @BeforeEach
    void seedContent() {
        author = seedAdmin();
        noticias = seedCategory("noticias", "Noticias");
    }

    @Test
    @DisplayName("sube una imagen, genera variantes y sirve el original")
    void subeYSirveImagen() throws IOException {
        byte[] png = png(1200, 800);
        ResponseEntity<MediaAssetResponse> uploaded = upload(png, "image/png", "portada.png",
                "Portada de prueba", "Foto: Blast Beast");

        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        MediaAssetResponse asset = uploaded.getBody();
        assertThat(asset).isNotNull();
        assertThat(asset.getKind().getValue()).isEqualTo("original");
        assertThat(asset.getMimeType()).isEqualTo("image/png");
        assertThat(asset.getWidth()).isEqualTo(1200);
        assertThat(asset.getHeight()).isEqualTo(800);
        assertThat(asset.getAltText()).isEqualTo("Portada de prueba");
        assertThat(asset.getCredit()).isEqualTo("Foto: Blast Beast");
        assertThat(asset.getVariants()).hasSize(2);
        assertThat(asset.getVariants())
                .extracting(variant -> variant.getKind().getValue())
                .containsExactlyInAnyOrder("thumb", "hero");
        assertThat(asset.getVariants())
                .filteredOn(variant -> variant.getKind().getValue().equals("thumb"))
                .singleElement()
                .extracting(MediaVariant::getWidth)
                .isEqualTo(480);

        ResponseEntity<byte[]> file = rest.getForEntity(URI.create(asset.getUrl()).getPath(),
                byte[].class);
        assertThat(file.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(file.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(file.getHeaders().getCacheControl()).contains("max-age=2592000");
        assertThat(file.getBody()).isEqualTo(png);
    }

    @Test
    @DisplayName("lista, actualiza y elimina medios")
    void listaActualizaYElimina() throws IOException {
        String token = loginAsAdmin();
        MediaAssetResponse asset = upload(png(320, 200), "image/png", "foto.png",
                "Foto antigua", "Crédito antiguo").getBody();

        ResponseEntity<MediaPage> list = rest.exchange(
                "/api/v1/admin/media?q=Foto",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(token)),
                MediaPage.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody().getTotalElements()).isEqualTo(1);

        ResponseEntity<MediaAssetResponse> updated = rest.exchange(
                "/api/v1/admin/media/" + asset.getId(),
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of(
                        "altText", "Foto nueva",
                        "credit", "Crédito nuevo"), jsonHeaders(token)),
                MediaAssetResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().getAltText()).isEqualTo("Foto nueva");
        assertThat(updated.getBody().getCredit()).isEqualTo("Crédito nuevo");

        ResponseEntity<Void> deleted = rest.exchange(
                "/api/v1/admin/media/" + asset.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(jsonHeaders(token)),
                Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ProblemDetail> missing = rest.getForEntity(
                URI.create(asset.getUrl()).getPath(), ProblemDetail.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("no elimina un medio usado como portada")
    void medioEnUso() throws IOException {
        String token = loginAsAdmin();
        MediaAssetResponse asset = upload(png(320, 200), "image/png", "portada.png",
                "Portada", null).getBody();
        MediaAsset media = mediaAssetRepository.findById(asset.getId()).orElseThrow();

        Article article = seedArticle("noticia-con-portada", "Noticia con portada",
                noticias, author, ArticleStatus.DRAFT, null);
        article.setCoverMedia(media);
        articleRepository.saveAndFlush(article);

        ResponseEntity<ProblemDetail> response = rest.exchange(
                "/api/v1/admin/media/" + asset.getId(),
                HttpMethod.DELETE,
                new HttpEntity<>(jsonHeaders(token)),
                ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getProperties()).containsEntry("code", "MED-004");
    }

    @Test
    @DisplayName("rechaza archivos vacíos y formatos no admitidos")
    void validacionesDeSubida() {
        ResponseEntity<ProblemDetail> empty = uploadProblem(new byte[0], "image/png", "vacia.png");
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(empty.getBody().getProperties()).containsEntry("code", "GEN-001");

        ResponseEntity<ProblemDetail> unsupported = uploadProblem("texto plano".getBytes(),
                "text/plain", "nota.txt");
        assertThat(unsupported.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(unsupported.getBody().getProperties()).containsEntry("code", "MED-001");
    }

    @Test
    @DisplayName("la biblioteca exige autenticación")
    void requiereAutenticacion() {
        assertThat(rest.getForEntity("/api/v1/admin/media", ProblemDetail.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<MediaAssetResponse> upload(byte[] content, String contentType,
            String filename, String altText, String credit) {
        return rest.postForEntity("/api/v1/admin/media",
                new HttpEntity<>(multipartBody(content, contentType, filename, altText, credit),
                        multipartHeaders()),
                MediaAssetResponse.class);
    }

    private ResponseEntity<ProblemDetail> uploadProblem(byte[] content, String contentType,
            String filename) {
        return rest.postForEntity("/api/v1/admin/media",
                new HttpEntity<>(multipartBody(content, contentType, filename, null, null),
                        multipartHeaders()),
                ProblemDetail.class);
    }

    private MultiValueMap<String, Object> multipartBody(byte[] content, String contentType,
            String filename, String altText, String credit) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(contentType));
        body.add("file", new HttpEntity<>(resource, partHeaders));
        if (altText != null) {
            body.add("altText", altText);
        }
        if (credit != null) {
            body.add("credit", credit);
        }
        return body;
    }

    private HttpHeaders multipartHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginAsAdmin());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return headers;
    }

    private byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.DARK_GRAY);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
