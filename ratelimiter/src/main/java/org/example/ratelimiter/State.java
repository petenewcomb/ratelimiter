package org.example.ratelimiter;

import java.util.function.Function;
import java.util.function.Supplier;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

class State {
  private final long originTick;
  private final Quota quota;
  private final AtomicLong availablePermits = new AtomicLong();
  private volatile long burstIntervalEndTick;
  private volatile long retentionTicks;

  State(final String quotaId, final long currentTick, final RateLimiter limiter) {
    this.originTick = currentTick;
    this.quota = new Quota(quotaId, this, limiter);
    setBurstIntervalValues(quotaId, currentTick, limiter);
  }

  boolean acquirePermit(final String quotaId, final long currentTick, final RateLimiter limiter) {
    if (currentTick >= burstIntervalEndTick) {
      updateBurstInterval(quotaId, currentTick, limiter);
    }
    return acquirePermitFast();
  }

  private boolean acquirePermitFast() {
    var remainingPermits = availablePermits.decrementAndGet();
    return remainingPermits >= 0;
  }

  private synchronized void updateBurstInterval(final String quotaId, final long currentTick, final RateLimiter limiter) {
    if (currentTick < burstIntervalEndTick) {
      // Another thread must have updated the interval while this one was waiting for the lock
      return;
    }
    setBurstIntervalValues(quotaId, currentTick, limiter);
  }

  private void setBurstIntervalValues(final String quotaId, final long currentTick, final RateLimiter limiter) {
    // Save these to ensure we use consistent values through the calculations
    final var quota = this.quota.get(quotaId, currentTick, this, limiter);
    final var burstIntervalTicks = limiter.burstIntervalTicks;  // In case it ends up not being final

    final var burstIntervalIndex = (currentTick - originTick) / burstIntervalTicks;

    // Start with an interval number that will yield at least one
    // permit. Without this, if the quota allows for only a fractional
    // permit per burst interval, the first request to acquire a
    // permit through a given State object will always fail.
    final var firstBurstIntervalNumber = 1 + calculateAverageTicksPerPermit(quota, limiter) / burstIntervalTicks;
    final var burstIntervalNumber = firstBurstIntervalNumber + burstIntervalIndex;

    // Using the difference between successive intervals ensures that
    // fractional quotas will be meeted out properly, even if that
    // means that some burst intervals get no permits.
    final var permitsForInterval = (long) (quota * (double) burstIntervalNumber) - (long) (quota * (double) (burstIntervalNumber-1));
    availablePermits.set(permitsForInterval);

    // Set this only after availablePermits is updated so that
    // acquirePermit() continues to block on updateBurstInterval()
    // until availablePermits is ready.
    burstIntervalEndTick = originTick + (burstIntervalIndex + 1) * burstIntervalTicks;
  }

  long getRetentionTicks(final String quotaId, final RateLimiter limiter) {
    final var quota = this.quota.get(quotaId, limiter.currentTickSupplier.get(), this, limiter);
    return Long.max(limiter.burstIntervalTicks, calculateAverageTicksPerPermit(quota, limiter));
  }

  private static long calculateAverageTicksPerPermit(final double quota, final RateLimiter limiter) {
    return (long) Math.ceil((double) limiter.quotaIntervalTicks / quota);
  }

  void quotaUpdated(final String id, final double quota, final RateLimiter limiter) {
    // TODO: consider recalculating permits for the current burst interval?
    limiter.updateRetention(id);
  }
}
