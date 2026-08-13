package dev.dashaun.cli.newsletter;

import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

public class RetryUtils {

    // Doubling is unbounded otherwise; a long attempt budget could sleep for many minutes.
    static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

    // Callers retry several endpoints in a row against the same host. Without jitter they
    // retry in lockstep, so a host that rate-limited the first call sees the retries arrive
    // together too. +/-25% keeps the overall window while desynchronising the attempts.
    private static final double JITTER_RATIO = 0.25;

    public static <T> T executeWithRetry(Callable<T> operation, int maxAttempts, Duration initialBackoff) {
        return executeWithRetry(operation, maxAttempts, initialBackoff, RetryUtils::isRetryableException);
    }

    public static <T> T executeWithRetry(Callable<T> operation, int maxAttempts, Duration initialBackoff,
                                         Predicate<Exception> retryPredicate) {
        Exception lastException = null;
        Duration backoff = cap(initialBackoff);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.call();
            } catch (Exception e) {
                lastException = e;

                if (attempt >= maxAttempts || !retryPredicate.test(e)) {
                    throw new RuntimeException("Operation failed after " + attempt + " attempts: "
                            + describe(e), e);
                }

                // An explicit Retry-After from the server beats our guess.
                Duration wait = retryAfter(e);
                if (wait == null) {
                    wait = applyJitter(backoff);
                }

                try {
                    Thread.sleep(cap(wait).toMillis());
                    backoff = cap(backoff.multipliedBy(2));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
            }
        }

        throw new RuntimeException("Operation failed: " + describe(lastException), lastException);
    }

    static Duration cap(Duration backoff) {
        return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
    }

    static Duration applyJitter(Duration backoff) {
        long millis = backoff.toMillis();
        if (millis <= 0) {
            return backoff;
        }
        long spread = (long) (millis * JITTER_RATIO);
        if (spread <= 0) {
            return backoff;
        }
        return Duration.ofMillis(millis + ThreadLocalRandom.current().nextLong(-spread, spread + 1));
    }

    /**
     * Honours a numeric {@code Retry-After} header (delta-seconds) when the server sends one.
     * Returns {@code null} when there is no usable value, so the caller falls back to backoff.
     */
    static Duration retryAfter(Exception e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof WebClientResponseException wcre && wcre.getHeaders() != null) {
                String header = wcre.getHeaders().getFirst("Retry-After");
                if (header != null) {
                    try {
                        long seconds = Long.parseLong(header.trim());
                        if (seconds >= 0) {
                            return cap(Duration.ofSeconds(seconds));
                        }
                    } catch (NumberFormatException ignored) {
                        // HTTP-date form; fall back to our own backoff.
                    }
                }
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return null;
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
