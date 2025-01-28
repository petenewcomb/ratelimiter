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
  private volatile long permitsForBurstWindow;
  private volatile long burstWindowEndTick;
  private volatile long retentionTicks;

  State(final String quotaId, final long currentTick, final RateLimiter limiter) {
    this.originTick = currentTick;
    this.quota = new Quota(quotaId, this, limiter);

    final var quota = this.quota.get(quotaId, currentTick, this, limiter);
    setBurstWindowValues(quota, currentTick, 0, limiter);
    this.retentionTicks = calculateRetentionTicks(quota, limiter);
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
    final var quota = this.quota.get(quotaId, currentTick, this, limiter);
    setBurstWindowValues(quota, currentTick, 0, limiter);
  }

  private void setBurstWindowValues(final double quota, final long currentTick, final long baselinePermits, final RateLimiter limiter) {
    // Save these to ensure we use consistent values through the calculations
    final var burstWindowTicks = limiter.burstWindowTicks;  // In case it ends up not being final
    final var burstWindowIndex = (currentTick - originTick) / burstWindowTicks;

    if (quota <= 0) {
      permitsForBurstWindow = 0;
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
      permitsForBurstWindow = (long) (quota * (double) burstWindowNumber) - (long) (quota * (double) (burstWindowNumber-1));
    }
    if (baselinePermits == 0) {
      availablePermits.set(permitsForBurstWindow);
    } else {
      availablePermits.addAndGet(permitsForBurstWindow - baselinePermits);
    }

    // Set this only after availablePermits is updated so that
    // acquirePermit() continues to block on updateBurstWindow()
    // until availablePermits is ready.
    burstWindowEndTick = originTick + (burstWindowIndex + 1) * burstWindowTicks;
  }

  long getRetentionTicks() {
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
    return (long) Math.ceil((double) limiter.quotaWindowTicks / quota);
  }

  void quotaUpdated(final String quotaId, final double quota, final RateLimiter limiter) {
    retentionTicks = calculateRetentionTicks(quota, limiter);
    limiter.updateRetention(quotaId);
    synchronized (this) {
      // Adjust permits for current burst window
      setBurstWindowValues(quota, limiter.currentTickSupplier.get(), permitsForBurstWindow, limiter);
    }
  }
}
