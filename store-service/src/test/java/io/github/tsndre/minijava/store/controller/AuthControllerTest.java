package io.github.tsndre.minijava.store.controller;

import io.github.tsndre.minijava.common.jwt.TokenPair;
import io.github.tsndre.minijava.store.constant.ErrorCode;
import io.github.tsndre.minijava.store.dto.request.LogoutRequest;
import io.github.tsndre.minijava.store.service.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends WebMvcTestSupport {

    @Test
    void theRefreshTokenIdStaysOutOfTheTokenPair() throws Exception {
        when(authService.login(any())).thenReturn(new TokenPair("access", "refresh", "refresh-id"));

        mvc.perform(post("/api/v1/auth/login").content("{\"email\":\"a@b.co\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data.access_token").value("access"))
                .andExpect(jsonPath("$.data.refresh_token").value("refresh"));
    }

    @Test
    void logout() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").content("{\"refresh_token\":\"token\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(startsWith("{\"data\":{\"message\":\"logged out\"}")));
        verify(authService).logout(new LogoutRequest("token"));
    }

    @Test
    void logoutWithAnInvalidToken() throws Exception {
        doThrow(new UnauthorizedException("invalid refresh token")).when(authService).logout(any());

        mvc.perform(post("/api/v1/auth/logout").content("{\"refresh_token\":\"token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.UNAUTHORIZED.value()))
                .andExpect(jsonPath("$.errors[0].message").value("invalid refresh token"));
    }
}
