package com.theblastbeast.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PingControllerTest {

    private final PingController controller = new PingController("0.1.0-SNAPSHOT");

    @Test
    @DisplayName("ping devuelve el servicio operativo con versión y marca de tiempo")
    void pingDevuelveElServicioOperativo() {
        var response = controller.ping();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("UP");
        assertThat(response.getBody().getApp()).isEqualTo("the-blast-beast-api");
        assertThat(response.getBody().getVersion()).isEqualTo("0.1.0-SNAPSHOT");
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }
}
