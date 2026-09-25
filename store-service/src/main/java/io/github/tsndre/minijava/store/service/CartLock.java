package io.github.tsndre.minijava.store.service;

import io.github.tsndre.minijava.store.constant.CacheKey;
import io.github.tsndre.minijava.store.service.exception.InternalException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The per-user cart lock shared by cart updates and checkout, so they never interleave on the same
 * cart. A Redis lock with the same defaults as the Go service's redsync mutex: an 8 second expiry
 * and up to 32 attempts, 50-250 ms apart.
 */
@Component
@RequiredArgsConstructor
public class CartLock {

    private static final Duration EXPIRY = Duration.ofSeconds(8);
    private static final int TRIES = 32;
    private static final int MIN_RETRY_DELAY_MILLIS = 50;
    private static final int MAX_RETRY_DELAY_MILLIS = 250;
    /** redsync's drift factor: the share of the expiry treated as clock drift. */
    private static final double DRIFT_FACTOR = 0.01;

    /** Deletes the lock only if it still holds our token, so an expired lock taken over by someone else survives. */
    private static final RedisScript<Long> UNLOCK_SCRIPT = RedisScript.of("""
            if redis.call("GET", KEYS[1]) == ARGV[1] then
                return redis.call("DEL", KEYS[1])
            else
                return 0
            end""", Long.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    /** A held lock; closing it releases the lock. */
    public interface Handle extends AutoCloseable {

        @Override
        void close();
    }

    /**
     * Takes the user's cart lock.
     *
     * @throws InternalException if the lock could not be taken
     */
    public Handle lock(UUID userId) {
        String key = CacheKey.CART_LOCK.formatted(userId);
        String token = newToken();

        for (int attempt = 0; attempt < TRIES; attempt++) {
            if (attempt > 0) {
                sleep(ThreadLocalRandom.current().nextInt(MIN_RETRY_DELAY_MILLIS, MAX_RETRY_DELAY_MILLIS));
            }
            long start = System.nanoTime();
            boolean acquired;
            try {
                acquired = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, EXPIRY));
            } catch (RuntimeException e) {
                acquired = false;
            }
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            Duration drift = Duration.ofMillis((long) (EXPIRY.toMillis() * DRIFT_FACTOR) + 2);
            if (acquired && elapsed.plus(drift).compareTo(EXPIRY) < 0) {
                return () -> unlock(key, token);
            }
            if (acquired) {
                unlock(key, token);
            }
        }
        throw new InternalException("failed to acquire cart lock, please try again");
    }

    private void unlock(String key, String token) {
        try {
            redis.execute(UNLOCK_SCRIPT, List.of(key), token);
        } catch (RuntimeException ignored) {
            // The lock expires on its own.
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InternalException("failed to acquire cart lock, please try again");
        }
    }
}
