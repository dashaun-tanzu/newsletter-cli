package dev.dashaun.cli.newsletter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.net.SocketException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryUtilsTest {

    private static final Duration FAST_BACKOFF = Duration.ofMillis(1);

    @Test
    void shouldSucceedOnFirstAttempt() {
        Callable<String> operation = () -> "success";

        String result = RetryUtils.executeWithRetry(operation, 3, FAST_BACKOFF);

        assertEquals("success", result);
    }

    @Test
    void shouldSucceedAfterRetries() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> operation = () -> {
            int attempts = attemptCount.incrementAndGet();
            if (attempts < 3) {
                throw new SocketException("connection reset by peer");
            }
            return "success after " + attempts + " attempts";
        };

        String result = RetryUtils.executeWithRetry(operation, 3, FAST_BACKOFF);

        assertEquals("success after 3 attempts", result);
        assertEquals(3, attemptCount.get());
    }

    @Test
    void shouldFailAfterMaxAttempts() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> operation = () -> {
            attemptCount.incrementAndGet();
            throw new TimeoutException("request timed out");
        };

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            RetryUtils.executeWithRetry(operation, 3, FAST_BACKOFF);
        });

        assertTrue(exception.getMessage().contains("failed after 3 attempts"));
        assertEquals(3, attemptCount.get());
    }

    @Test
    void shouldNotRetryOnNonRetryableException() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> operation = () -> {
            attemptCount.incrementAndGet();
            throw new IllegalArgumentException("Invalid argument");
        };

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            RetryUtils.executeWithRetry(operation, 3, FAST_BACKOFF);
        });

        assertEquals(1, attemptCount.get());
        assertEquals("Invalid argument", exception.getCause().getMessage());
    }

    @Test
    void shouldUseCustomRetryPredicate() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> operation = () -> {
            int attempts = attemptCount.incrementAndGet();
            if (attempts < 2) {
                throw new IllegalStateException("Not a timeout");
            }
            return "success";
        };

        String result = RetryUtils.executeWithRetry(
            operation,
            3,
            FAST_BACKOFF,
            e -> e.getMessage() != null && e.getMessage().contains("Not a timeout")
        );

        assertEquals("success", result);
        assertEquals(2, attemptCount.get());
    }

    @Test
    void shouldRespectMaxAttempts() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> operation = () -> {
            attemptCount.incrementAndGet();
            throw new IOException("connection refused");
        };

        assertThrows(RuntimeException.class, () -> {
            RetryUtils.executeWithRetry(operation, 2, FAST_BACKOFF);
        });

        assertEquals(2, attemptCount.get());
    }

    @Test
    void shouldRetryOnWebClient5xx() {
        assertTrue(RetryUtils.isRetryableException(
                WebClientResponseException.create(503, "Service Unavailable", null, null, null)));
        assertTrue(RetryUtils.isRetryableException(
                WebClientResponseException.create(500, "Internal Server Error", null, null, null)));
    }

    @Test
    void shouldRetryOnWebClient429() {
        WebClientResponseException tooManyRequests =
                WebClientResponseException.create(HttpStatus.TOO_MANY_REQUESTS.value(),
                        "Too Many Requests", null, null, null);
        assertTrue(RetryUtils.isRetryableException(tooManyRequests));
    }

    @Test
    void shouldRetryOnWebClient408() {
        WebClientResponseException requestTimeout =
                WebClientResponseException.create(HttpStatus.REQUEST_TIMEOUT.value(),
                        "Request Timeout", null, null, null);
        assertTrue(RetryUtils.isRetryableException(requestTimeout));
    }

    @Test
    void shouldNotRetryOnWebClient4xxOtherThan408Or429() {
        assertFalse(RetryUtils.isRetryableException(
                WebClientResponseException.create(400, "Bad Request", null, null, null)));
        assertFalse(RetryUtils.isRetryableException(
                WebClientResponseException.create(404, "Not Found", null, null, null)));
    }

    @Test
    void shouldRetryOnTimeoutException() {
        assertTrue(RetryUtils.isRetryableException(new TimeoutException("timed out")));
    }

    @Test
    void shouldRetryOnIoException() {
        assertTrue(RetryUtils.isRetryableException(new IOException("boom")));
        assertTrue(RetryUtils.isRetryableException(new SocketException("connection reset")));
    }

    @Test
    void shouldWalkCauseChainForRetryableException() {
        RuntimeException wrapper = new RuntimeException("wrapped",
                new RuntimeException("inner", new SocketException("reset")));
        assertTrue(RetryUtils.isRetryableException(wrapper));
    }

    @Test
    void shouldNotRetryWhenCauseChainHasNoRetryable() {
        RuntimeException nested = new RuntimeException("outer",
                new IllegalStateException("inner"));
        assertFalse(RetryUtils.isRetryableException(nested));
    }
}
