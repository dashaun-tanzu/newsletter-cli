package dev.dashaun.cli.newsletter;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryUtilsTest {

    @Test
    void shouldSucceedOnFirstAttempt() throws Exception {
        Callable<String> operation = () -> "success";
        
        String result = RetryUtils.executeWithRetry(operation, 3, Duration.ofSeconds(1));
        
        assertEquals("success", result);
    }

    @Test
    void shouldSucceedAfterRetries() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> operation = () -> {
            int attempts = attemptCount.incrementAndGet();
            if (attempts < 3) {
                throw new RuntimeException("Connection reset");
            }
            return "success after " + attempts + " attempts";
        };
        
        String result = RetryUtils.executeWithRetry(operation, 3, Duration.ofSeconds(1));
        
        assertEquals("success after 3 attempts", result);
        assertEquals(3, attemptCount.get());
    }

    @Test
    void shouldFailAfterMaxAttempts() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> operation = () -> {
            attemptCount.incrementAndGet();
            throw new RuntimeException("Connection timeout");
        };
        
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            RetryUtils.executeWithRetry(operation, 3, Duration.ofSeconds(1));
        });
        
        assertTrue(exception.getMessage().contains("failed after 3 attempts"));
        assertEquals(3, attemptCount.get());
    }

    @Test
    void shouldNotRetryOnNonRetryableException() {
        AtomicInteger attemptCount = new AtomicInteger(0);
        Callable<String> operation = () -> {
            attemptCount.incrementAndGet();
            throw new RuntimeException("Invalid argument");
        };
        
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            RetryUtils.executeWithRetry(operation, 3, Duration.ofSeconds(1));
        });
        
        assertEquals(1, attemptCount.get());
        assertEquals("Invalid argument", exception.getCause().getMessage());
    }

    @Test
    void shouldUseCustomRetryPredicate() throws Exception {
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
            Duration.ofSeconds(1),
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
            throw new RuntimeException("connection reset");
        };
        
        assertThrows(RuntimeException.class, () -> {
            RetryUtils.executeWithRetry(operation, 2, Duration.ofSeconds(1));
        });
        
        assertEquals(2, attemptCount.get());
    }
}
