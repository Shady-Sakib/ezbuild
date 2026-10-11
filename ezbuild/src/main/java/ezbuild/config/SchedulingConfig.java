package ezbuild.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables scheduled jobs (order expiry, payment reconciliation) and async mail. */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig { }
