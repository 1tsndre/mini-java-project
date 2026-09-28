package io.github.tsndre.minijava.store.controller;

import io.github.tsndre.minijava.store.constant.ErrorCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoutingTest extends WebMvcTestSupport {

    @BeforeAll
    static void uploadedFile() throws Exception {
        Files.createDirectories(UPLOAD_DIR.resolve("products"));
        Files.writeString(UPLOAD_DIR.resolve("products/photo.png"), "png-bytes");
    }

    @Test
    void healthAnswersInTheEnvelopeEndingWithANewline() throws Exception {
        MvcResult result = mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.meta.request_id").isNotEmpty())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).endsWith("}\n");
    }

    @Test
    void theClientsRequestIdIsKept() throws Exception {
        mvc.perform(get("/health").header("X-Request-ID", "abc-123"))
                .andExpect(header().string("X-Request-ID", "abc-123"))
                .andExpect(jsonPath("$.meta.request_id").value("abc-123"));
    }

    @Test
    void unknownRoutesAreAJson404() throws Exception {
        for (String path : List.of("/nope", "/api/v1/products/", "/api/v1")) {
            mvc.perform(get(path))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.NOT_FOUND.value()))
                    .andExpect(jsonPath("$.errors[0].message").value("not found"));
        }
    }

    /** The Go router's catch-all route answers a known path with an unknown method with a 404, never a 405. */
    @Test
    void anUnsupportedMethodIsA404() throws Exception {
        mvc.perform(request(HttpMethod.DELETE, "/api/v1/products"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].message").value("not found"));
        mvc.perform(options("/api/v1/products"))
                .andExpect(status().isNotFound());
    }

    @Test
    void headIsServedForGetRoutes() throws Exception {
        when(categoryService.getAllCategories()).thenReturn(List.of());
        mvc.perform(head("/api/v1/categories")).andExpect(status().isOk());
    }

    @Test
    void uploadsServeFilesButNotDirectoryListings() throws Exception {
        mvc.perform(get("/uploads/products/photo.png"))
                .andExpect(status().isOk())
                .andExpect(content().string("png-bytes"))
                .andExpect(header().string("Content-Type", "image/png"));

        mvc.perform(get("/uploads/")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].message").value("not found"));
        mvc.perform(get("/uploads/products/")).andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("photo.png"))));

        // A directory redirects to its trailing-slash form, which is refused above.
        mvc.perform(get("/uploads/products"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "products/"))
                .andExpect(content().string(""));
    }

    /** Like Go's router, the bare prefix redirects to the directory with a 307 and a small HTML body. */
    @Test
    void theUploadsPrefixRedirectsToTheDirectory() throws Exception {
        mvc.perform(get("/uploads?x=1"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(header().string("Location", "/uploads/?x=1"))
                .andExpect(content().string("<a href=\"/uploads/?x=1\">Temporary Redirect</a>.\n\n"));
        mvc.perform(head("/docs"))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(header().string("Location", "/docs/"))
                .andExpect(content().string(""));
    }

    @Test
    void aMissingFileIsGosPlainTextNotFound() throws Exception {
        mvc.perform(get("/uploads/products/missing.png"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string("404 page not found\n"));
    }
}
