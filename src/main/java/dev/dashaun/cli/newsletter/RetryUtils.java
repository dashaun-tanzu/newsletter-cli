package dev.dashaun.cli.newsletter;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

public class RetryUtils {

    public static <T> T executeWithRetry(Callable<T> operation, int maxAttempts, Duration initialBackoff) {
        return executeWithRetry(operation, maxAttempts, initialBackoff, RetryUtils::isRetryableException);
    }

    public static <T> T executeWithRetry(Callable<T> operation, int maxAttempts, Duration initialBackoff, 
                                         Predicate<Exception> retryPredicate) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.call();
            } catch (Exception e) {
                lastException = e;
                
                if (attempt >= maxAttempts || !retryPredicate.test(e)) {
                    throw new RuntimeException("Operation failed after " + attempt + " attempts", e);
                }
                
                try {
                    Thread.sleep(initialBackoff.toMillis());
                    initialBackoff = initialBackoff.multipliedBy(2);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
            }
        }

        throw new RuntimeException("Operation failed", lastException);
    }

    private static boolean isRetryableException(Exception e) {
        if (e instanceof RuntimeException) {
            String message = e.getMessage();
            return message != null && (message.contains("timeout") || message.contains("connect") || message.contains("reset"));
        }
        return false;
    }
}
