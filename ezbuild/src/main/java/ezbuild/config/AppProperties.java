package ezbuild.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** All ezbuild.* settings in one typed record; values come from env vars via application.properties. */
@ConfigurationProperties(prefix = "ezbuild")
public record AppProperties(
        String baseUrl,
        String uploadDir,
        boolean seed,
        String seedAdminPassword,
        String rememberMeKey,
        String corsOrigins,
        boolean mongoTransactions,
        String mailFrom,
        Jwt jwt,
        Shipping shipping,
        Payment payment,
        SslCommerz sslcommerz,
        Stripe stripe,
        Advisor advisor) {

    public record Jwt(String secret, int accessMinutes, int refreshDays) { }

    public record Shipping(long inside, long outside) { }

    public record Payment(long codFee, int reservationMinutes) { }

    public record SslCommerz(String storeId, String storePass, String base, long maxAmount) {
        public boolean configured() {
            return storeId != null && !storeId.isBlank() && storePass != null && !storePass.isBlank();
        }
    }

    public record Stripe(String secretKey, String webhookSecret, double bdtPerUsd) {
        public boolean configured() {
            return secretKey != null && !secretKey.isBlank();
        }
    }

    public record Advisor(String mode, String url, String key) { }
}
