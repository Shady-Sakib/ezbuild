package ezbuild.assistant;

import java.time.Instant;

/** Simple circuit breaker: after N failures in a row, skip calls until the cool-down passes. */
public class CircuitBreaker {

    private final int threshold;
    private final long coolDownSeconds;
    private int failures;
    private Instant openUntil = Instant.EPOCH;

    public CircuitBreaker(int threshold, long coolDownSeconds) {
        this.threshold = threshold;
        this.coolDownSeconds = coolDownSeconds;
    }

    public synchronized boolean allow() {
        return Instant.now().isAfter(openUntil);
    }

    public synchronized void success() {
        failures = 0;
    }

    public synchronized void failure() {
        failures++;
        if (failures >= threshold) {
            openUntil = Instant.now().plusSeconds(coolDownSeconds);
            failures = 0;
        }
    }
}
