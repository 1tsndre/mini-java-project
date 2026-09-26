package io.github.tsndre.minijava.store.controller;

import io.github.tsndre.minijava.common.response.ApiResponse;
import io.github.tsndre.minijava.common.response.Meta;
import io.github.tsndre.minijava.common.response.Responses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<ApiResponse> health(Meta meta) {
        return Responses.success(HttpStatus.OK, Map.of("status", "ok"), meta);
    }
}
