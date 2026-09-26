package io.github.tsndre.minijava.store.controller;

import io.github.tsndre.minijava.common.response.ApiResponse;
import io.github.tsndre.minijava.common.response.Meta;
import io.github.tsndre.minijava.common.response.Responses;
import io.github.tsndre.minijava.store.constant.ErrorCode;
import io.github.tsndre.minijava.store.constant.RateLimitKey;
import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.dto.request.CheckoutRequest;
import io.github.tsndre.minijava.store.dto.request.UpdateOrderStatusRequest;
import io.github.tsndre.minijava.store.dto.response.PaymentStatusResponse;
import io.github.tsndre.minijava.store.grpc.PaymentStatusClient;
import io.github.tsndre.minijava.store.pagination.Pages;
import io.github.tsndre.minijava.store.service.OrderService;
import io.github.tsndre.minijava.store.web.ApiException;
import io.github.tsndre.minijava.store.web.Authenticated;
import io.github.tsndre.minijava.store.web.RateLimited;
import io.github.tsndre.minijava.store.web.RequireRole;
import io.github.tsndre.minijava.store.web.bind.CurrentUserId;
import io.github.tsndre.minijava.store.web.bind.JsonBody;
import io.github.tsndre.minijava.store.web.bind.PathUuid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

import static io.github.tsndre.minijava.store.controller.Validation.REQUIRED;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrderController {

    private static final String INVALID_ID = "invalid order id";

    private final OrderService orderService;
    private final PaymentStatusClient paymentClient;

    @PostMapping("/orders")
    @Authenticated
    @RequireRole(Role.BUYER)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> checkout(Meta meta, @CurrentUserId UUID userId, @JsonBody CheckoutRequest req) {
        if (req.shippingAddress().isEmpty()) {
            Validation.reject("shipping_address", REQUIRED);
        }

        return Responses.success(HttpStatus.CREATED, orderService.checkout(userId, req.shippingAddress()), meta);
    }

    @GetMapping("/orders")
    @Authenticated
    @RequireRole(Role.BUYER)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> getOrders(Meta meta, @CurrentUserId UUID userId, HttpServletRequest request) {
        long page = Pages.parse(request.getParameter("page"));
        long perPage = Pages.parse(request.getParameter("per_page"));

        return Paging.respond(orderService.getOrders(userId, page, perPage), page, perPage, meta);
    }

    @GetMapping("/orders/{id}")
    @Authenticated
    @RequireRole(Role.BUYER)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> getOrder(Meta meta, @CurrentUserId UUID userId,
                                                @PathUuid(value = "id", message = INVALID_ID) UUID id) {
        return Responses.success(HttpStatus.OK, orderService.getOrderById(userId, id), meta);
    }

    @PutMapping("/orders/{id}/cancel")
    @Authenticated
    @RequireRole(Role.BUYER)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> cancelOrder(Meta meta, @CurrentUserId UUID userId,
                                                   @PathUuid(value = "id", message = INVALID_ID) UUID id) {
        orderService.cancelOrder(userId, id);
        return Responses.success(HttpStatus.OK, Map.of("message", "order cancelled"), meta);
    }

    @GetMapping("/orders/{id}/payment")
    @Authenticated
    @RequireRole(Role.BUYER)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> getOrderPayment(Meta meta, @CurrentUserId UUID userId,
                                                       @PathUuid(value = "id", message = INVALID_ID) UUID id) {
        orderService.getOrderById(userId, id);

        PaymentStatusResponse status;
        try {
            status = paymentClient.getStatus(id.toString());
        } catch (RuntimeException e) {
            throw ApiException.of(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.INTERNAL, "payment service unavailable");
        }

        return Responses.success(HttpStatus.OK, status, meta);
    }

    @GetMapping("/seller/orders")
    @Authenticated
    @RequireRole(Role.SELLER)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> getSellerOrders(Meta meta, @CurrentUserId UUID userId,
                                                       HttpServletRequest request) {
        long page = Pages.parse(request.getParameter("page"));
        long perPage = Pages.parse(request.getParameter("per_page"));

        return Paging.respond(orderService.getSellerOrders(userId, page, perPage), page, perPage, meta);
    }

    @PutMapping("/orders/{id}/status")
    @Authenticated
    @RequireRole(Role.SELLER)
    @RateLimited(RateLimitKey.AUTH)
    public ResponseEntity<ApiResponse> updateOrderStatus(Meta meta, @CurrentUserId UUID userId,
                                                         @PathUuid(value = "id", message = INVALID_ID) UUID id,
                                                         @JsonBody UpdateOrderStatusRequest req) {
        if (req.status().isEmpty()) {
            Validation.reject("status", REQUIRED);
        }

        orderService.updateOrderStatus(userId, id, req.status());
        return Responses.success(HttpStatus.OK, Map.of("message", "order status updated"), meta);
    }
}
