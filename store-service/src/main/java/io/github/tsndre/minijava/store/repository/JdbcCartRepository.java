package io.github.tsndre.minijava.store.repository;

import io.github.tsndre.minijava.store.constant.CacheKey;
import io.github.tsndre.minijava.store.model.Cart;
import io.github.tsndre.minijava.store.model.CartItem;
import io.github.tsndre.minijava.store.model.CartItemDb;
import io.github.tsndre.minijava.store.repository.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcCartRepository implements CartRepository {

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final Cache cache;

    @Override
    public Cart getCart(UUID userId) {
        String cacheKey = CacheKey.CART.formatted(userId);

        Optional<Cart> cached = cache.get(cacheKey, Cart.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        Cart cart = new Cart(userId);
        try {
            cart.setItems(new ArrayList<>(jdbc.sql("""
                            SELECT ci.product_id, ci.quantity, p.name, p.price, p.image_url
                            FROM cart_items ci
                            JOIN products p ON p.id = ci.product_id
                            WHERE ci.user_id = ?""")
                    .param(userId)
                    .query(RowMappers.CART_ITEM)
                    .list()));
        } catch (DataAccessException e) {
            throw DbErrors.translate(e);
        }

        cache.set(cacheKey, cart, CacheKey.TTL_CART);
        return cart;
    }

    @Override
    public void saveCart(Cart cart) {
        try {
            transactions.executeWithoutResult(status -> {
                jdbc.sql("DELETE FROM cart_items WHERE user_id = ?").param(cart.getUserId()).update();

                if (cart.getItems().isEmpty()) {
                    return;
                }

                List<CartItemDb> rows = new ArrayList<>(cart.getItems().size());
                for (CartItem item : cart.getItems()) {
                    rows.add(CartItemDb.builder()
                            .userId(cart.getUserId())
                            .productId(item.getProductId())
                            .quantity(item.getQuantity())
                            .build());
                }

                StringBuilder sql = new StringBuilder("INSERT INTO cart_items (user_id, product_id, quantity) VALUES ");
                List<Object> args = new ArrayList<>();
                for (CartItemDb row : rows) {
                    sql.append(args.isEmpty() ? "(?, ?, ?)" : ", (?, ?, ?)");
                    args.add(row.getUserId());
                    args.add(row.getProductId());
                    args.add(row.getQuantity());
                }
                jdbc.sql(sql.toString()).params(args).update();
            });
        } catch (DataAccessException e) {
            throw DbErrors.translate(e);
        }

        cache.set(CacheKey.CART.formatted(cart.getUserId()), cart, CacheKey.TTL_CART);
    }

    @Override
    public void deleteCart(UUID userId) {
        try {
            jdbc.sql("DELETE FROM cart_items WHERE user_id = ?").param(userId).update();
        } catch (DataAccessException e) {
            throw DbErrors.translate(e);
        }
        cache.delete(CacheKey.CART.formatted(userId));
    }
}
