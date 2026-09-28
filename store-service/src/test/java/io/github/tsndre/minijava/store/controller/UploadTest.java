package io.github.tsndre.minijava.store.controller;

import io.github.tsndre.minijava.store.constant.ErrorCode;
import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.service.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadTest extends WebMvcTestSupport {

    private final UUID userId = UUID.randomUUID();
    private final UUID storeId = UUID.randomUUID();

    private MockMultipartFile logo(String filename) {
        return new MockMultipartFile("logo", filename, "image/png", "png-bytes".getBytes());
    }

    @Test
    void storesTheFileAndPassesItsPathToTheService() throws Exception {
        when(storeService.updateLogo(eq(userId), eq(storeId), any())).thenReturn(null);

        mvc.perform(as(userId, Role.SELLER, multipart("/api/v1/stores/" + storeId + "/logo").file(logo("me.PNG"))))
                .andExpect(status().isOk());

        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(storeService).updateLogo(eq(userId), eq(storeId), path.capture());
        assertThat(path.getValue()).matches("stores/[0-9a-f-]{36}\\.png");
        assertThat(Files.readString(UPLOAD_DIR.resolve(path.getValue()))).isEqualTo("png-bytes");
    }

    @Test
    void theStoredFileIsDeletedWhenTheServiceRefuses() throws Exception {
        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        when(storeService.updateLogo(eq(userId), eq(storeId), path.capture()))
                .thenThrow(new ForbiddenException("forbidden: not store owner"));

        mvc.perform(as(userId, Role.SELLER, multipart("/api/v1/stores/" + storeId + "/logo").file(logo("me.png"))))
                .andExpect(status().isForbidden());

        assertThat(UPLOAD_DIR.resolve(path.getValue())).doesNotExist();
    }

    @Test
    void aDisallowedExtension() throws Exception {
        mvc.perform(as(userId, Role.SELLER, multipart("/api/v1/stores/" + storeId + "/logo").file(logo("me.gif"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.VALIDATION.value()))
                .andExpect(jsonPath("$.errors[0].message").value("file extension .gif is not allowed"));
    }

    @Test
    void aMissingFile() throws Exception {
        mvc.perform(as(userId, Role.SELLER, multipart("/api/v1/products/" + storeId + "/image").file(logo("me.png"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("image file is required"));
    }

    @Test
    void aRequestThatIsNotMultipart() throws Exception {
        mvc.perform(as(userId, Role.SELLER, post("/api/v1/stores/" + storeId + "/logo").content("{}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("logo file is required"));
    }
}
