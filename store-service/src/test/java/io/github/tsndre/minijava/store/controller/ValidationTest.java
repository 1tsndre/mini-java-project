package io.github.tsndre.minijava.store.controller;

import io.github.tsndre.minijava.store.constant.ErrorCode;
import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.constant.ValidationLimit;
import io.github.tsndre.minijava.store.model.ProductFilter;
import io.github.tsndre.minijava.store.repository.PageResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inputs that PostgreSQL or bcrypt would reject must fail validation with a 400 before reaching a
 * service.
 */
class ValidationTest extends WebMvcTestSupport {

    private static final String LONG = "a".repeat(ValidationLimit.MAX_VARCHAR_LENGTH + 1);
    private final UUID userId = UUID.randomUUID();
    private final String pathId = UUID.randomUUID().toString();

    private void assertFieldError(MockHttpServletRequestBuilder request, String field, String message) throws Exception {
        mvc.perform(request.contentType("application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.VALIDATION.value()))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].message").value(message));
    }

    @Test
    void registerEmailTooLong() throws Exception {
        assertFieldError(post("/api/v1/auth/register")
                        .content("{\"email\":\"" + LONG + "@example.com\",\"password\":\"secret1\",\"name\":\"A\"}"),
                "email", "maximum 255 characters");
    }

    @Test
    void registerNameTooLong() throws Exception {
        assertFieldError(post("/api/v1/auth/register")
                        .content("{\"email\":\"a@example.com\",\"password\":\"secret1\",\"name\":\"" + LONG + "\"}"),
                "name", "maximum 255 characters");
    }

    @Test
    void registerPasswordLongerThanBcryptAccepts() throws Exception {
        assertFieldError(post("/api/v1/auth/register").content("{\"email\":\"a@example.com\",\"password\":\""
                        + "p".repeat(ValidationLimit.MAX_PASSWORD_BYTES + 1) + "\",\"name\":\"A\"}"),
                "password", "maximum 72 bytes");
    }

    @Test
    void registerPasswordLengthIsCountedInBytes() throws Exception {
        // Three characters, six bytes: long enough.
        when(authService.register(any())).thenReturn(null);
        mvc.perform(post("/api/v1/auth/register")
                        .content("{\"email\":\"a@example.com\",\"password\":\"ééé\",\"name\":\"A\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void registerListsEveryInvalidField() throws Exception {
        mvc.perform(post("/api/v1/auth/register").content("{\"email\":\"not-an-email\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[0].message").value("invalid email format"))
                .andExpect(jsonPath("$.errors[1].message").value("minimum 6 characters"))
                .andExpect(jsonPath("$.errors[2].field").value("name"));
        verifyNoInteractions(authService);
    }

    @Test
    void createStoreNameTooLong() throws Exception {
        assertFieldError(as(userId, Role.BUYER, post("/api/v1/stores").content("{\"name\":\"" + LONG + "\"}")),
                "name", "maximum 255 characters");
    }

    @Test
    void updateStoreNameTooLong() throws Exception {
        assertFieldError(as(userId, Role.SELLER, put("/api/v1/stores/" + pathId).content("{\"name\":\"" + LONG + "\"}")),
                "name", "maximum 255 characters");
    }

    @Test
    void createCategoryNameTooLong() throws Exception {
        assertFieldError(as(userId, Role.ADMIN, post("/api/v1/categories").content("{\"name\":\"" + LONG + "\"}")),
                "name", "maximum 255 characters");
    }

    @Test
    void updateCategoryNameTooLong() throws Exception {
        assertFieldError(as(userId, Role.ADMIN, put("/api/v1/categories/" + pathId).content("{\"name\":\"" + LONG + "\"}")),
                "name", "maximum 255 characters");
    }

    @Test
    void createProductNameTooLong() throws Exception {
        assertFieldError(as(userId, Role.SELLER, post("/api/v1/products").content("{\"name\":\"" + LONG
                        + "\",\"price\":\"10.00\",\"stock\":1,\"category_id\":\"" + UUID.randomUUID() + "\"}")),
                "name", "maximum 255 characters");
    }

    @Test
    void updateProductNameTooLong() throws Exception {
        assertFieldError(as(userId, Role.SELLER, put("/api/v1/products/" + pathId).content("{\"name\":\"" + LONG + "\"}")),
                "name", "maximum 255 characters");
    }

    @Test
    void listProductsRejectsACategoryIdThatIsNotAUuid() throws Exception {
        assertFieldError(get("/api/v1/products?category_id=abc"), "category_id", "must be a valid UUID");
    }

    @Test
    void listProductsRejectsAStoreIdThatIsNotAUuid() throws Exception {
        assertFieldError(get("/api/v1/products?store_id=1%27%20or%201%3D1"), "store_id", "must be a valid UUID");
    }

    @Test
    void varcharLimitCountsCharactersNotBytes() {
        // "é" is two bytes; PostgreSQL's VARCHAR(255) limit is in characters.
        assertThat(Validation.exceedsVarchar("é".repeat(ValidationLimit.MAX_VARCHAR_LENGTH))).isFalse();
        assertThat(Validation.exceedsVarchar("é".repeat(ValidationLimit.MAX_VARCHAR_LENGTH + 1))).isTrue();
    }

    @Test
    void listProductsPassesIdFiltersInCanonicalForm() throws Exception {
        UUID categoryId = UUID.randomUUID();
        when(productService.getProducts(any())).thenReturn(new PageResult<>(List.of(), 0));

        // Upper case and braces parse as a UUID; the canonical form goes to the query.
        mvc.perform(get("/api/v1/products").param("category_id", "{" + categoryId.toString().toUpperCase() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.pagination.current_page").value(1))
                .andExpect(jsonPath("$.meta.pagination.per_page").value(10));

        ArgumentCaptor<ProductFilter> filter = ArgumentCaptor.forClass(ProductFilter.class);
        verify(productService).getProducts(filter.capture());
        assertThat(filter.getValue().categoryId()).isEqualTo(categoryId.toString());
        assertThat(filter.getValue().storeId()).isEmpty();
    }
}
