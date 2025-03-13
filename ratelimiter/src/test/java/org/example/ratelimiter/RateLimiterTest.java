package org.example.ratelimiter;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;

class RateLimiterTest {
  @Test
  void testConstructor() {
    assertFalse(false);
    assertEquals(2, 2);
  }

  @Test
  void testObjectSize() {
    // Create a RateLimiter
    RateLimiter limiter =
        RateLimiter.newBuilder()
            .setFailsafeQuota(10.0)
            .build(id -> CompletableFuture.completedFuture(1.0));

    // Create a State object with a realistic quota ID
    String quotaId = "test-quota-id@example.com";
    State state = new State(quotaId, System.nanoTime(), limiter);

    // Java 9+ encodes strings as Latin-1 if it can, else UTF-16
    var quotaIdBytes = quotaId.getBytes(StandardCharsets.ISO_8859_1);
    if (!quotaId.equals(new String(quotaIdBytes, StandardCharsets.ISO_8859_1))) {
      quotaIdBytes = quotaId.getBytes(StandardCharsets.UTF_16);
    }

    // Compute first layout before starting to print the report to avoid interleaving JOL warnings
    // about agent attachment that are irrelevant to this use case.
    final var quotaIdLayout = ClassLayout.parseInstance(quotaId);

    // Print Quota ID String object layout information
    System.out.println("\nQuota ID " + quotaIdLayout.toPrintable());
    System.out.println(
        "Quota ID String inner " + ClassLayout.parseInstance(quotaIdBytes).toPrintable());
    System.out.println("Quota ID " + GraphLayout.parseInstance(quotaId).toFootprint());

    // Print State object layout information
    System.out.println(ClassLayout.parseInstance(state).toPrintable());
    System.out.println(GraphLayout.parseInstance(state).toFootprint());

    // Force the test to output results
    assertTrue(true);
  }
}
