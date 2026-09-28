package io.github.tsndre.minijava.store.controller;

import io.github.tsndre.minijava.store.constant.ErrorCode;
import io.github.tsndre.minijava.store.constant.OrderStatus;
import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.dto.response.OrderResponse;
import io.github.tsndre.minijava.store.service.exception.ConflictException;
import io.github.tsndre.minijava.store.service.exception.ForbiddenException;
import io.github.tsndre.minijava.store.service.exception.InsufficientStockException;
import io.github.tsndre.minijava.store.service.exception.InternalException;
import io.github.tsndre.minijava.store.service.exception.InvalidStatusException;
import io.github.tsndre.minijava.store.service.exception.NotFoundException;
import io.github.tsndre.minijava.store.service.exception.UnauthorizedException;
import io.github.tsndre.minijava.store.service.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every service failure is answered with the status and error code the Go handlers use. */
class ErrorMappingTest extends WebMvcTestSupport {

    private final UUID userId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    /**
     * A seller-supplied status is echoed in the error message; words like "failed" in it must not
     * change the response (the Go service once turned them into a 500).
     */
    @ParameterizedTest
    @ValueSource(strings = {"failed", "not found", "forbidden", "unknown"})
    void invalidStatusIs400WhateverItSays(String requested) throws Exception {
        doThrow(new InvalidStatusException("invalid status transition from paid to " + requested))
                .when(orderService).updateOrderStatus(userId, orderId, requested);

        mvc.perform(as(userId, Role.SELLER, put("/api/v1/orders/" + orderId + "/status")
                        .content("{\"status\":\"" + requested + "\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.INVALID_STATUS.value()));
    }

    /** A product name is part of the message, so it must not steer the status either. */
    @ParameterizedTest
    @ValueSource(strings = {"Lost and not found mug", "failed prototype tee", "Plain mug"})
    void insufficientStockIs400WhateverTheProductName(String name) throws Exception {
        when(orderService.checkout(userId, "Jl. Test 1"))
                .thenThrow(new InsufficientStockException("insufficient stock for product " + name));

        mvc.perform(as(userId, Role.BUYER, post("/api/v1/orders").content("{\"shipping_address\":\"Jl. Test 1\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.INSUFFICIENT_STOCK.value()))
                .andExpect(jsonPath("$.errors[0].message").value("insufficient stock for product " + name));
    }

    @Test
    void addCartItemInsufficientStockCode() throws Exception {
        when(cartService.addItem(eq(userId), any())).thenThrow(new InsufficientStockException("insufficient stock"));

        mvc.perform(as(userId, Role.BUYER, post("/api/v1/cart/items")
                        .content("{\"product_id\":\"" + UUID.randomUUID() + "\",\"quantity\":2}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.INSUFFICIENT_STOCK.value()));
    }

    @Test
    void eachServiceErrorKindHasItsStatusAndCode() throws Exception {
        expect(new NotFoundException("order not found"), 404, ErrorCode.NOT_FOUND);
        expect(new ForbiddenException("forbidden"), 403, ErrorCode.FORBIDDEN);
        expect(new ConflictException("conflict"), 409, ErrorCode.CONFLICT);
        expect(new ValidationException("cart is empty"), 400, ErrorCode.VALIDATION);
        expect(new UnauthorizedException("nope"), 401, ErrorCode.UNAUTHORIZED);
        expect(new InternalException("failed to fetch orders"), 500, ErrorCode.INTERNAL);
        expect(new InvalidStatusException("cannot cancel order with status shipped"), 400, ErrorCode.INVALID_STATUS);
    }

    private void expect(RuntimeException error, int status, ErrorCode code) throws Exception {
        doThrow(error).when(orderService).getOrderById(userId, orderId);

        mvc.perform(as(userId, Role.BUYER, get("/api/v1/orders/" + orderId)))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.errors[0].code").value(code.value()))
                .andExpect(jsonPath("$.errors[0].message").value(error.getMessage()));
    }

    @Test
    void unexpectedErrorsAreA500WithoutDetails() throws Exception {
        when(orderService.getOrderById(userId, orderId)).thenThrow(new DataAccessResourceFailureException("secret"));

        mvc.perform(as(userId, Role.BUYER, get("/api/v1/orders/" + orderId)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.INTERNAL.value()))
                .andExpect(jsonPath("$.errors[0].message").value("internal server error"));
    }

    @Test
    void paymentServiceDownIs503() throws Exception {
        when(orderService.getOrderById(userId, orderId)).thenReturn(new OrderResponse(orderId, userId, UUID.randomUUID(),
                OrderStatus.PENDING, null, "", null, null, null, null));
        when(paymentStatusClient.getStatus(anyString())).thenThrow(new RuntimeException("UNAVAILABLE"));

        mvc.perform(as(userId, Role.BUYER, get("/api/v1/orders/" + orderId + "/payment")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.INTERNAL.value()))
                .andExpect(jsonPath("$.errors[0].message").value("payment service unavailable"));
    }

    @Test
    void invalidPathIdsNameTheResource() throws Exception {
        mvc.perform(get("/api/v1/stores/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("invalid store id"));
        mvc.perform(as(userId, Role.BUYER, put("/api/v1/cart/items/xyz").content("{\"quantity\":1}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("invalid product id"));
    }

    @Test
    void anAccessTokenWhoseUserIdIsNotAUuidIsAnInvalidUser() throws Exception {
        String token = jwtManager.generateTokenPair("not-a-uuid", "x@y.co", Role.BUYER.value()).accessToken();

        mvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].message").value("invalid user"));
    }
}
