package dev.dashaun.cli.newsletter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NewsletterApplication {

	public static void main(String[] args) {
		// SpringApplication.exit consults the ExitCodeTracker bean, so a section that failed
		// every retry surfaces as a non-zero exit instead of a silently incomplete document.
		System.exit(SpringApplication.exit(SpringApplication.run(NewsletterApplication.class, args)));
	}

}
