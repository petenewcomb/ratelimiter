package org.example.ratelimiter;

import java.util.function.Function;
import java.util.function.Supplier;

public class RateLimiter {
  private final Supplier<Long> monotonicClock;
  private final Long burstIntervalTicks;
  private final Double ticksPerQuotaUnit;
  private final Function<String, Double> getQuotaById;

  public RateLimiter(
      final Supplier<Long> monotonicClock,
      final Long burstIntervalTicks,
      final Double ticksPerQuotaUnit,
      final Function<String, Double> getQuotaById) {
    this.monotonicClock = monotonicClock;
    this.burstIntervalTicks = burstIntervalTicks;
    this.ticksPerQuotaUnit = ticksPerQuotaUnit;
    this.getQuotaById = getQuotaById;
  }

  public boolean acquirePermit(final String quotaId) {
    final var quota = this.getQuotaById.apply(quotaId);
    return quota != null && quota >= 1.0;
  }
}
