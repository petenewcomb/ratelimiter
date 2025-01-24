package org.example.ratelimiter;

import java.util.function.Function;
import java.util.function.Supplier;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Duration;

class State {
  private final long originTick;
  private final Quota quota;
  private final AtomicLong availablePermits = new AtomicLong();
  private volatile long burstWindowEndTick;
  private volatile long retentionTicks;

  State(final String quotaId, final long currentTick, final RateLimiter limiter) {
    this.originTick = currentTick;
    this.quota = new Quota(quotaId, this, limiter);
    setBurstWindowValues(quotaId, currentTick, limiter);
    this.retentionTicks = calculateRetentionTicks(quota.get(quotaId, currentTick, this, limiter), limiter);
  }

  boolean acquirePermit(final String quotaId, final long currentTick, final RateLimiter limiter) {
    if (currentTick >= burstWindowEndTick) {
      updateBurstWindow(quotaId, currentTick, limiter);
    }
    return acquirePermitFast();
  }

  private boolean acquirePermitFast() {
    var remainingPermits = availablePermits.decrementAndGet();
    return remainingPermits >= 0;
  }

  private synchronized void updateBurstWindow(final String quotaId, final long currentTick, final RateLimiter limiter) {
    if (currentTick < burstWindowEndTick) {
      // Another thread must have updated the window while this one was waiting for the lock
      return;
    }
    setBurstWindowValues(quotaId, currentTick, limiter);
  }

  private void setBurstWindowValues(final String quotaId, final long currentTick, final RateLimiter limiter) {
    // Save these to ensure we use consistent values through the calculations
    final var quota = this.quota.get(quotaId, currentTick, this, limiter);

    final var burstWindowTicks = limiter.burstWindowTicks;  // In case it ends up not being final
    final var burstWindowIndex = (currentTick - originTick) / burstWindowTicks;

    final long permitsForWindow;
    if (quota <= 0) {
      permitsForWindow = 0;
    } else {
      // Start with an window number that will yield at least one
      // permit. Without this, if the quota allows for only a fractional
      // permit per burst window, the first request to acquire a
      // permit through a given State object will always fail.
      final var firstBurstWindowNumber = 1 + calculateAverageTicksPerPermit(quota, limiter) / burstWindowTicks;
      final var burstWindowNumber = firstBurstWindowNumber + burstWindowIndex;

      // Using the difference between successive windows ensures that
      // fractional quotas will be meeted out properly, even if that
      // means that some burst windows get no permits.
      permitsForWindow = (long) (quota * (double) burstWindowNumber) - (long) (quota * (double) (burstWindowNumber-1));
    }
    availablePermits.set(permitsForWindow);

    // Set this only after availablePermits is updated so that
    // acquirePermit() continues to block on updateBurstWindow()
    // until availablePermits is ready.
    burstWindowEndTick = originTick + (burstWindowIndex + 1) * burstWindowTicks;
  }

  long getRetentionTicks(final String quotaId, final RateLimiter limiter) {
    return retentionTicks;
  }

  private long calculateRetentionTicks(final double quota, final RateLimiter limiter) {
    if (quota <= 0.0) {
      // Don't want to retain infinitely
      return limiter.maxRetentionTicks;
    }
    // Retain for at least one burst window or as long as it takes for one permit to be available.
    return Long.min(
        limiter.maxRetentionTicks,
        Long.max(
            limiter.burstWindowTicks,
            calculateAverageTicksPerPermit(quota, limiter)));
  }

  private static long calculateAverageTicksPerPermit(final double quota, final RateLimiter limiter) {
    final var value = (long) Math.ceil((double) limiter.quotaWindowTicks / quota);
    System.out.printf("calculateAverageTicksPerPermit quota = %s; value = %s\n", quota, value);
    return value;
  }

  void quotaUpdated(final String id, final double quota, final RateLimiter limiter) {
    retentionTicks = calculateRetentionTicks(quota, limiter);
    // TODO: consider recalculating permits for the current burst window?
    limiter.updateRetention(id);
  }
}
