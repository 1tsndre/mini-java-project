package io.github.tsndre.minijava.store.service;

import io.github.tsndre.minijava.store.dto.request.AddCartItemRequest;
import io.github.tsndre.minijava.store.dto.request.UpdateCartItemRequest;
import io.github.tsndre.minijava.store.dto.response.CartItemResponse;
import io.github.tsndre.minijava.store.dto.response.CartResponse;
import io.github.tsndre.minijava.store.model.Cart;
import io.github.tsndre.minijava.store.model.CartItem;
import io.github.tsndre.minijava.store.model.Product;
import io.github.tsndre.minijava.store.repository.CartRepository;
import io.github.tsndre.minijava.store.repository.ProductRepository;
import io.github.tsndre.minijava.store.service.exception.InsufficientStockException;
import io.github.tsndre.minijava.store.service.exception.InternalException;
import io.github.tsndre.minijava.store.service.exception.NotFoundException;
import io.github.tsndre.minijava.store.service.exception.ValidationException;
import io.github.tsndre.minijava.store.util.Uuids;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService {

    static final String INSUFFICIENT_STOCK = "insufficient stock";

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final CartLock cartLock;

    public CartResponse getCart(UUID userId) {
        Cart cart;
        try {
            cart = cartRepository.getCart(userId);
        } catch (RuntimeException e) {
            throw new InternalException("failed to fetch cart");
        }
        refreshItems(cart);
        return toCartResponse(cart);
    }

    public CartResponse addItem(UUID userId, AddCartItemRequest req) {
        UUID productId = Uuids.parse(req.productId())
                .orElseThrow(() -> new ValidationException("invalid product_id"));

        if (req.quantity() <= 0) {
            throw new ValidationException("quantity must be greater than 0");
        }

        Product product = findProduct(productId);
        if (product.getStock() < req.quantity()) {
            throw new InsufficientStockException(INSUFFICIENT_STOCK);
        }

        try (CartLock.Handle ignored = cartLock.lock(userId)) {
            // getCart returns an empty cart when the user has none, so an error here is a real
            // failure. Saving over it would replace the whole cart with this one item.
            Cart cart = loadCart(userId);

            boolean found = false;
            for (CartItem item : cart.getItems()) {
                if (item.getProductId().equals(productId)) {
                    if (product.getStock() < item.getQuantity() + req.quantity()) {
                        throw new InsufficientStockException(INSUFFICIENT_STOCK);
                    }
                    item.setQuantity((int) (item.getQuantity() + req.quantity()));
                    found = true;
                    break;
                }
            }

            if (!found) {
                cart.getItems().add(CartItem.builder()
                        .productId(productId)
                        .name(product.getName())
                        .price(product.getPrice())
                        .quantity(req.quantity().intValue())
                        .imageUrl(product.getImageUrl())
                        .build());
            }

            refreshItems(cart, product);
            return save(cart);
        }
    }

    public CartResponse updateItem(UUID userId, UUID productId, UpdateCartItemRequest req) {
        if (req.quantity() <= 0) {
            throw new ValidationException("quantity must be greater than 0");
        }

        try (CartLock.Handle ignored = cartLock.lock(userId)) {
            Cart cart = loadCart(userId);

            CartItem item = cart.getItems().stream()
                    .filter(i -> i.getProductId().equals(productId))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("item not found in cart"));

            Product product = findProduct(productId);
            if (product.getStock() < req.quantity()) {
                throw new InsufficientStockException(INSUFFICIENT_STOCK);
            }
            item.setQuantity(req.quantity().intValue());

            refreshItems(cart, product);
            return save(cart);
        }
    }

    public CartResponse removeItem(UUID userId, UUID productId) {
        try (CartLock.Handle ignored = cartLock.lock(userId)) {
            Cart cart = loadCart(userId);

            CartItem item = cart.getItems().stream()
                    .filter(i -> i.getProductId().equals(productId))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("item not found in cart"));
            cart.getItems().remove(item);

            refreshItems(cart);
            return save(cart);
        }
    }

    /**
     * Sets each item's name, price and image to the product's current values. The stored values
     * are a snapshot from when the item was added, while checkout charges the current price, so
     * showing the snapshot could show a total the buyer will not actually pay. Products the caller
     * already loaded are reused; if a product cannot be loaded, its stored values are kept.
     */
    private void refreshItems(Cart cart, Product... loaded) {
        Map<UUID, Product> known = new HashMap<>();
        for (Product product : loaded) {
            known.put(product.getId(), product);
        }
        for (CartItem item : cart.getItems()) {
            Product product = known.get(item.getProductId());
            if (product == null) {
                Optional<Product> found;
                try {
                    found = productRepository.findById(item.getProductId());
                } catch (RuntimeException e) {
                    continue;
                }
                if (found.isEmpty()) {
                    continue;
                }
                product = found.get();
            }
            item.setName(product.getName());
            item.setPrice(product.getPrice());
            item.setImageUrl(product.getImageUrl());
        }
    }

    private Cart loadCart(UUID userId) {
        try {
            return cartRepository.getCart(userId);
        } catch (RuntimeException e) {
            throw new InternalException("failed to load cart");
        }
    }

    private CartResponse save(Cart cart) {
        cart.setUpdatedAt(OffsetDateTime.now());
        try {
            cartRepository.saveCart(cart);
        } catch (RuntimeException e) {
            throw new InternalException("failed to save cart");
        }
        return toCartResponse(cart);
    }

    private Product findProduct(UUID productId) {
        try {
            return productRepository.findById(productId).orElseThrow();
        } catch (RuntimeException e) {
            throw new NotFoundException("product not found");
        }
    }

    static CartResponse toCartResponse(Cart cart) {
        BigDecimal total = BigDecimal.ZERO;
        List<CartItemResponse> items = new ArrayList<>(cart.getItems().size());

        for (CartItem item : cart.getItems()) {
            BigDecimal subtotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            total = total.add(subtotal);
            items.add(new CartItemResponse(item.getProductId(), item.getName(), item.getPrice(),
                    item.getQuantity(), subtotal, item.getImageUrl()));
        }

        return new CartResponse(items, total, cart.getUpdatedAt());
    }
}
