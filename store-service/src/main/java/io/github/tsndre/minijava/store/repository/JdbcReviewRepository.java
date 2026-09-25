package io.github.tsndre.minijava.store.repository;

import io.github.tsndre.minijava.store.constant.OrderStatus;
import io.github.tsndre.minijava.store.model.Review;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcReviewRepository implements ReviewRepository {

    static final String COLUMNS = "id, user_id, product_id, rating, comment, created_at, updated_at";

    private final JdbcClient jdbc;

    @Override
    public Review create(Review review) {
        try {
            return jdbc.sql("""
                            INSERT INTO reviews (user_id, product_id, rating, comment)
                            VALUES (?, ?, ?, ?)
                            RETURNING %s""".formatted(COLUMNS))
                    .params(review.getUserId(), review.getProductId(), review.getRating(), review.getComment())
                    .query(RowMappers.REVIEW)
                    .single();
        } catch (DataAccessException e) {
            throw DbErrors.translate(e);
        }
    }

    @Override
    public PageResult<Review> findByProductId(UUID productId, long page, int perPage) {
        long total = jdbc.sql("SELECT COUNT(*) FROM reviews WHERE product_id = ?")
                .param(productId)
                .query(Long.class)
                .single();

        List<Review> reviews = jdbc.sql("""
                        SELECT r.id, r.user_id, r.product_id, r.rating, r.comment, r.created_at, r.updated_at,
                            u.name AS user_name
                        FROM reviews r
                        JOIN users u ON u.id = r.user_id
                        WHERE r.product_id = ?
                        ORDER BY r.created_at DESC, r.id DESC
                        LIMIT ? OFFSET ?""")
                .params(productId, perPage, (page - 1) * perPage)
                .query(RowMappers.REVIEW_WITH_USER_NAME)
                .list();
        return new PageResult<>(reviews, total);
    }

    @Override
    public boolean hasUserReviewed(UUID userId, UUID productId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM reviews WHERE user_id = ? AND product_id = ?)")
                .params(userId, productId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public boolean hasUserPurchased(UUID userId, UUID productId) {
        return jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM order_items oi
                            JOIN orders o ON o.id = oi.order_id
                            WHERE o.user_id = ? AND oi.product_id = ? AND o.status IN (?, ?)
                        )""")
                .params(userId, productId, OrderStatus.SHIPPED.value(), OrderStatus.COMPLETED.value())
                .query(Boolean.class)
                .single();
    }
}
