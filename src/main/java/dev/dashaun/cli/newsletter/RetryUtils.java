package dev.dashaun.cli.newsletter;

import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
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
                    throw new RuntimeException("Operation failed after " + attempt + " attempts: "
                            + describe(e), e);
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

        throw new RuntimeException("Operation failed: " + describe(lastException), lastException);
    }

    static boolean isRetryableException(Exception e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof WebClientResponseException wcre) {
                int status = wcre.getStatusCode().value();
                if (status >= 500 || status == 408 || status == 429) {
                    return true;
                }
            } else if (t instanceof WebClientRequestException
                    || t instanceof TimeoutException
                    || t instanceof IOException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private static String describe(Throwable e) {
        if (e == null) {
            return "unknown";
        }
        StringBuilder sb = new StringBuilder(e.getClass().getSimpleName())
                .append(": ").append(e.getMessage());
        Throwable cause = e.getCause();
        if (cause != null && cause != e) {
            sb.append(" (cause: ").append(cause.getClass().getSimpleName())
                    .append(": ").append(cause.getMessage()).append(")");
        }
        return sb.toString();
    }
}
