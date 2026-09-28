package io.github.tsndre.minijava.store.controller;

import io.github.tsndre.minijava.common.jwt.TokenPair;
import io.github.tsndre.minijava.store.constant.ErrorCode;
import io.github.tsndre.minijava.store.dto.request.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Request bodies are decoded the way Go's json.Decoder reads them. */
class JsonBodyTest extends WebMvcTestSupport {

    @BeforeEach
    void loginSucceeds() {
        when(authService.login(any())).thenReturn(new TokenPair("access", "refresh"));
    }

    private LoginRequest loginRequestSeenByTheService() {
        ArgumentCaptor<LoginRequest> request = ArgumentCaptor.forClass(LoginRequest.class);
        verify(authService).login(request.capture());
        return request.getValue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "{", "[]", "\"text\"", "123", "{\"email\":1,\"password\":\"x\"}", "{'email':'a'}",
            "{\"email\":\"a\",}"})
    void malformedBodiesAreInvalid(String body) throws Exception {
        mvc.perform(post("/api/v1/auth/login").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.VALIDATION.value()))
                .andExpect(jsonPath("$.errors[0].message").value("invalid request body"));
    }

    @Test
    void jsonNullIsAnEmptyRequest() throws Exception {
        mvc.perform(post("/api/v1/auth/login").content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message").value("is required"))
                .andExpect(jsonPath("$.errors[1].field").value("password"));
    }

    @Test
    void theContentTypeIsIgnored() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("text/plain")
                        .content("{\"email\":\"a@b.co\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.access_token").value("access"));
    }

    @Test
    void onlyTheFirstJsonValueIsRead() throws Exception {
        mvc.perform(post("/api/v1/auth/login").content("{\"email\":\"a@b.co\",\"password\":\"secret\"} trailing"))
                .andExpect(status().isOk());
        assertThat(loginRequestSeenByTheService().email()).isEqualTo("a@b.co");
    }

    @Test
    void fieldNamesMatchCaseInsensitively() throws Exception {
        mvc.perform(post("/api/v1/auth/login").content("{\"Email\":\"a@b.co\",\"PASSWORD\":\"secret\"}"))
                .andExpect(status().isOk());
        assertThat(loginRequestSeenByTheService().password()).isEqualTo("secret");
    }

    @Test
    void invalidUtf8IsReplacedNotRejected() throws Exception {
        byte[] body = "{\"email\":\"a\u0000@b.co\",\"password\":\"x\"}".getBytes(StandardCharsets.UTF_8);
        // Swap the escaped NUL for a lone continuation byte, which is not valid UTF-8.
        String text = new String(body, StandardCharsets.UTF_8).replace("\u0000", "");
        byte[] raw = text.replace("a@", "aÿ@").getBytes(StandardCharsets.ISO_8859_1);
        raw[raw.length - 1] = '}';
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] == (byte) 0xff) {
                raw[i] = (byte) 0x80;
            }
        }

        mvc.perform(post("/api/v1/auth/login").content(raw)).andExpect(status().isOk());
        assertThat(loginRequestSeenByTheService().email()).isEqualTo("a�@b.co");
    }
}
