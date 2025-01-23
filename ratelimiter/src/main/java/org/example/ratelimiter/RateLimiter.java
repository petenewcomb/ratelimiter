package org.example.ratelimiter;

import java.util.function.Function;
import java.util.function.Supplier;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.time.Duration;

public class RateLimiter {

  // Injected resources
  final Supplier<Long> currentTickSupplier;
  final long burstIntervalTicks;
  final long quotaIntervalTicks;
  final long quotaRefreshIntervalTicks;
  final double failsafeQuota;
  final Function<String, Double> getQuotaById;
  final BiConsumer<String, Throwable> recordQuotaFetchError;

  private final Cache<String, State> cache;

  public static class Builder {
    Supplier<Long> getCurrentTick = System::nanoTime;
    Duration tickDuration = Duration.ofNanos(1);
    Duration burstInterval = Duration.ofSeconds(1);
    Duration quotaInterval = Duration.ofSeconds(1);
    Duration quotaRefreshInterval = Duration.ofMinutes(1);
    double failsafeQuota = 1.0;
    BiConsumer<String, Throwable> recordQuotaFetchError = (id, e) -> {};

    Builder() {}

    public Builder setMonotonicClock(final Supplier<Long> getCurrentTick, final Duration tickDuration) {
      this.getCurrentTick = Objects.requireNonNull(getCurrentTick);
      this.tickDuration = Objects.requireNonNull(tickDuration);
      return this;
    }

    public Builder setBurstInterval(final Duration value) {
      this.burstInterval = Objects.requireNonNull(value);
    }

    public Builder setQuotaInterval(final Duration value) {
      this.quotaInterval = Objects.requireNonNull(value);
    }

    public Builder setQuotaRefreshInterval(final Duration value) {
      this.quotaRefreshInterval = Objects.requireNonNull(value);
    }

    public Builder setFailsafeQuota(final double value) {
      this.failsafeQuota = value;
    }

    public Builder setRecordQuotaFetchError(final BiConsumer<String, Throwable> value) {
      this.recordQuotaFetchError = Objects.requireNonNull(value);
    }

    public RateLimiter build(final Function<String, Double> getQuotaById) {
      return new RateLimiter(this, Objects.requireNonNull(getQuotaById));
    }
  }

  public Builder newBuilder() {
    return new Builder();
  }

  RateLimiter(
      final Builder builder,
      final Function<String, Double> getQuotaById) {
    this.monotonicClock = builder.monotonicClock;
    this.burstIntervalTicks = builder.burstInterval.getNanos() / builder.tickDuration.getNanos();
    this.failsafeQuota = builder.failsafeQuota;
    this.getQuotaById = getQuotaById;
    this.cache = Caffeine.newBuilder()
        .ticker(monotonicClock)
        .expireAfterRead(Expiry.accessing((quotaId, state) -> state.getRetentionTicks(this)))
        .build();
  }

  public boolean acquirePermit(final String quotaId) {
    return cache.get(quotaId, this::newState).acquirePermit(getMonotonicTime);
  }

  private State newState(final String quotaId) {
    final var quota = getQuotaById.apply(quotaId);
  }

  void updateRetention(final String quotaId) {
    // Force the cache to update the expiration
    cache.getIfPresent(quotaId);
  }
}
