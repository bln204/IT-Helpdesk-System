package com.example.ticketing;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class TicketingApplication {

	public static void main(String[] args) {
		applyHostedProfileFallback();
		applyKoyebDatabaseFallback();
		SpringApplication.run(TicketingApplication.class, args);
	}

	private static void applyHostedProfileFallback() {
		if (hasText(System.getenv("SPRING_DATASOURCE_URL"))
			|| hasText(System.getenv("DATABASE_URL"))
			|| hasText(System.getenv("DATABASE_HOST"))
			|| hasText(System.getenv("SPRING_PROFILES_ACTIVE"))) {
			return;
		}

		if (hasText(System.getenv("PORT"))) {
			System.setProperty("spring.profiles.active", "koyeb");
		}
	}

	private static void applyKoyebDatabaseFallback() {
		if (hasText(System.getenv("SPRING_DATASOURCE_URL"))) {
			return;
		}

		if (applyDatabasePartsFallback()) {
			return;
		}

		String databaseUrl = System.getenv("DATABASE_URL");
		if (!hasText(databaseUrl)) {
			return;
		}

		try {
			URI uri = new URI(databaseUrl);
			String scheme = uri.getScheme();
			if (!"postgres".equalsIgnoreCase(scheme) && !"postgresql".equalsIgnoreCase(scheme)) {
				return;
			}

			StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
				.append(uri.getHost());
			if (uri.getPort() != -1) {
				jdbcUrl.append(':').append(uri.getPort());
			}
			jdbcUrl.append(uri.getPath());
			if (hasText(uri.getQuery())) {
				jdbcUrl.append('?').append(uri.getQuery());
			}

			System.setProperty("spring.datasource.url", jdbcUrl.toString());

			if (!hasText(System.getenv("SPRING_DATASOURCE_USERNAME")) && hasText(uri.getUserInfo())) {
				String[] credentials = uri.getUserInfo().split(":", 2);
				if (credentials.length > 0 && hasText(credentials[0])) {
					System.setProperty("spring.datasource.username", credentials[0]);
				}
				if (credentials.length > 1 && hasText(credentials[1])) {
					System.setProperty("spring.datasource.password", credentials[1]);
				}
			}
		} catch (URISyntaxException ignored) {
			// If DATABASE_URL is malformed, Spring will fall back to its normal config path.
		}
	}

	private static boolean applyDatabasePartsFallback() {
		String host = System.getenv("DATABASE_HOST");
		String name = System.getenv("DATABASE_NAME");
		if (!hasText(host) || !hasText(name)) {
			return false;
		}

		String port = hasText(System.getenv("DATABASE_PORT")) ? System.getenv("DATABASE_PORT") : "5432";
		String sslMode = hasText(System.getenv("DATABASE_SSL_MODE")) ? System.getenv("DATABASE_SSL_MODE") : "require";
		String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + name + "?sslmode=" + sslMode;
		System.setProperty("spring.datasource.url", jdbcUrl);

		if (!hasText(System.getenv("SPRING_DATASOURCE_USERNAME"))) {
			String username = System.getenv("DATABASE_USER");
			if (hasText(username)) {
				System.setProperty("spring.datasource.username", username);
			}
		}

		if (!hasText(System.getenv("SPRING_DATASOURCE_PASSWORD"))) {
			String password = System.getenv("DATABASE_PASSWORD");
			if (hasText(password)) {
				System.setProperty("spring.datasource.password", password);
			}
		}

		return true;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

}
