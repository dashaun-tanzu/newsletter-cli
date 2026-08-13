package dev.dashaun.cli.newsletter;

import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

/**
 * Lets a command report a degraded run without aborting the remaining sections.
 *
 * <p>Shell commands return their output as a String, so a failed section would otherwise exit 0
 * and look successful to the GitHub Action driving this CLI. Marking a failure here makes
 * {@code SpringApplication.exit} return a non-zero code once all sections have been attempted.
 */
@Component
public class ExitCodeTracker implements ExitCodeGenerator {

    private volatile int exitCode = 0;

    public void markFailure() {
        this.exitCode = 1;
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
