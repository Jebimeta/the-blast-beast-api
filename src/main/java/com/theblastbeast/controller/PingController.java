package com.theblastbeast.controller;

import com.theblastbeast.api.SystemApiDelegate;
import com.theblastbeast.domain.vo.PingResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PingController implements SystemApiDelegate {

    private static final String APP_NAME = "the-blast-beast-api";

    private final String version;

    public PingController(@Value("${info.app.version:unknown}") String version) {
        this.version = version;
    }

    @Override
    public ResponseEntity<PingResponse> ping() {
        log.debug("INIT - PingController -> ping()");

        PingResponse response = new PingResponse();
        response.setStatus("UP");
        response.setApp(APP_NAME);
        response.setVersion(version);
        response.setTimestamp(OffsetDateTime.now(ZoneOffset.UTC));

        log.debug("END - PingController -> ping()");
        return ResponseEntity.ok(response);
    }
}
