package dev.dashaun.cli.newsletter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
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

    @Test
    void shouldCapBackoffAtMaximum() {
        assertEquals(RetryUtils.MAX_BACKOFF, RetryUtils.cap(Duration.ofHours(1)));
        assertEquals(Duration.ofSeconds(5), RetryUtils.cap(Duration.ofSeconds(5)));
    }

    @Test
    void jitterShouldStayWithinTwentyFivePercentOfBackoff() {
        Duration base = Duration.ofSeconds(8);
        for (int i = 0; i < 200; i++) {
            long millis = RetryUtils.applyJitter(base).toMillis();
            assertTrue(millis >= 6000 && millis <= 10000,
                    "jittered backoff out of range: " + millis + "ms");
        }
    }

    @Test
    void jitterShouldLeaveTinyBackoffUnchanged() {
        // Sub-4ms backoffs have no room for a +/-25% spread; they must not become negative.
        assertEquals(Duration.ofMillis(1), RetryUtils.applyJitter(Duration.ofMillis(1)));
        assertEquals(Duration.ZERO, RetryUtils.applyJitter(Duration.ZERO));
    }

    @Test
    void shouldHonourNumericRetryAfterHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "12");
        WebClientResponseException throttled = WebClientResponseException.create(
                429, "Too Many Requests", headers, null, null);

        assertEquals(Duration.ofSeconds(12), RetryUtils.retryAfter(throttled));
    }

    @Test
    void shouldCapRetryAfterHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "86400");
        WebClientResponseException throttled = WebClientResponseException.create(
                429, "Too Many Requests", headers, null, null);

        assertEquals(RetryUtils.MAX_BACKOFF, RetryUtils.retryAfter(throttled));
    }

    @Test
    void shouldIgnoreHttpDateRetryAfterAndFallBackToBackoff() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "Wed, 21 Oct 2026 07:28:00 GMT");
        WebClientResponseException throttled = WebClientResponseException.create(
                429, "Too Many Requests", headers, null, null);

        assertNull(RetryUtils.retryAfter(throttled));
    }

    @Test
    void shouldReturnNullRetryAfterWhenHeaderAbsent() {
        assertNull(RetryUtils.retryAfter(new IOException("boom")));
        assertNull(RetryUtils.retryAfter(
                WebClientResponseException.create(503, "Service Unavailable", null, null, null)));
    }

    @Test
    void shouldFindRetryAfterThroughCauseChain() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "3");
        WebClientResponseException throttled = WebClientResponseException.create(
                429, "Too Many Requests", headers, null, null);
        RuntimeException wrapped = new RuntimeException("outer", new RuntimeException("inner", throttled));

        assertEquals(Duration.ofSeconds(3), RetryUtils.retryAfter(wrapped));
    }
}
