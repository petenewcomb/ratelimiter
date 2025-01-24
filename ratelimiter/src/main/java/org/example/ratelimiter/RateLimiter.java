package org.example.ratelimiter;

import java.util.function.Function;
import java.util.function.Supplier;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Expiry;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.Objects;
import java.time.Duration;

public class RateLimiter {

  // Injected resources
  final Supplier<Long> currentTickSupplier;
  final Duration tickDuration;
  final long burstWindowTicks;
  final long maxRetentionTicks;
  final long quotaWindowTicks;
  final long quotaRefreshPeriodTicks;
  final double failsafeQuota;
  final Function<String, CompletableFuture<Double>> getQuotaById;
  final BiConsumer<String, Throwable> quotaFetchErrorRecorder;

  private final Cache<String, State> cache;

  public static class Builder {
    Supplier<Long> currentTickSupplier = System::nanoTime;
    Duration tickDuration = Duration.ofNanos(1);
    Duration burstWindowDuration = Duration.ofSeconds(1);
    Duration maxRetentionDuration = Duration.ofMinutes(1);
    Duration quotaWindowDuration = Duration.ofSeconds(1);
    Duration quotaRefreshPeriod = Duration.ofMinutes(1);
    double failsafeQuota = 1.0;
    BiConsumer<String, Throwable> quotaFetchErrorRecorder = (id, e) -> {
      throw new RuntimeException("Unhandled quota fetch error; please fix bug or supply a handler to RateLimiter.Builder.setQuotaFetchErrorRecorder: " + e.getMessage(), e);
    };

    Builder() {}

    public Builder setMonotonicClock(final Supplier<Long> currentTickSupplier, final Duration tickDuration) {
      this.currentTickSupplier = Objects.requireNonNull(currentTickSupplier);
      this.tickDuration = Objects.requireNonNull(tickDuration);
      return this;
    }

    public Builder setBurstWindowDuration(final Duration value) {
      this.burstWindowDuration = Objects.requireNonNull(value);
      return this;
    }

    public Builder setMaxRetentionDuration(final Duration value) {
      this.maxRetentionDuration = Objects.requireNonNull(value);
      return this;
    }

    public Builder setQuotaWindowDuration(final Duration value) {
      this.quotaWindowDuration = Objects.requireNonNull(value);
      return this;
    }

    public Builder setQuotaRefreshPeriod(final Duration value) {
      this.quotaRefreshPeriod = Objects.requireNonNull(value);
      return this;
    }

    public Builder setFailsafeQuota(final double value) {
      this.failsafeQuota = value;
      return this;
    }

    public Builder setQuotaFetchErrorRecorder(final BiConsumer<String, Throwable> value) {
      this.quotaFetchErrorRecorder = Objects.requireNonNull(value);
      return this;
    }

    // The future returned by the supplied function may resolve to
    // Double.NaN to indicate that there is no value available for the
    // given quota ID.  In this case, the limiter will continue to use
    // whatever value already in use.  This value may be the
    // configured failsafe quota unless getQuotaById had previously
    // resolved to a non-NaN value within the lifetime of the state
    // currently retained for that quota ID.
    public RateLimiter build(final Function<String, CompletableFuture<Double>> getQuotaById) {
      return new RateLimiter(this, Objects.requireNonNull(getQuotaById));
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  RateLimiter(
      final Builder builder,
      final Function<String, CompletableFuture<Double>> getQuotaById) {
    this.currentTickSupplier = builder.currentTickSupplier;
    this.tickDuration = builder.tickDuration;
    this.burstWindowTicks = builder.burstWindowDuration.toNanos() / builder.tickDuration.toNanos();
    this.maxRetentionTicks = builder.maxRetentionDuration.toNanos() / builder.tickDuration.toNanos();
    this.quotaWindowTicks = builder.quotaWindowDuration.toNanos() / builder.tickDuration.toNanos();
    this.quotaRefreshPeriodTicks = builder.quotaRefreshPeriod.toNanos() / builder.tickDuration.toNanos();
    this.failsafeQuota = builder.failsafeQuota;
    this.quotaFetchErrorRecorder = builder.quotaFetchErrorRecorder;
    this.getQuotaById = getQuotaById;
    this.cache = Caffeine.newBuilder()
        .ticker(currentTickSupplier::get)
        .expireAfter(Expiry.accessing(this::getEntryRetentionPeriod))
        .build();
  }

  public boolean acquirePermit(final String quotaId) {
    final long currentTick = currentTickSupplier.get();
    return cache.get(quotaId, id -> new State(id, currentTick, this))
        .acquirePermit(quotaId, currentTick, this);
  }

  private Duration getEntryRetentionPeriod(final String quotaId, final State state) {
    return tickDuration.multipliedBy(state.getRetentionTicks(quotaId, this));
  }

  void updateRetention(final String quotaId) {
    // Force the cache to update the expiration
    cache.getIfPresent(quotaId);
  }
}
