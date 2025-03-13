package org.example.ratelimiter;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

// Extend rather than contain Quota to avoid additional object memory overhead
class State extends Quota {
  private final long originTick;

  // Use AtomicLongFieldUpdater to avoid the object memory overhead of AtomicLong
  private volatile long availablePermits;
  private static final AtomicLongFieldUpdater<State> AVAILABLE_PERMITS_UPDATER =
      AtomicLongFieldUpdater.newUpdater(State.class, "availablePermits");

  private volatile long permitsForBurstWindow;
  private volatile long burstWindowEndTick;
  private volatile long retentionTicks;

  State(final String quotaId, final long currentTick, final RateLimiter limiter) {
    super();
    this.originTick = currentTick;

    // Initialize quota fetching before getting initial quota value in case the
    // fetch is synchronous.
    initializeQuota(quotaId, this, limiter);

    final var quotaValue = getQuota(quotaId, currentTick, this, limiter);
    setBurstWindowValues(quotaValue, currentTick, 0, limiter);
    this.retentionTicks = calculateRetentionTicks(quotaValue, limiter);
  }

  boolean acquirePermit(final String quotaId, final long currentTick, final RateLimiter limiter) {
    if (currentTick >= burstWindowEndTick) {
      updateBurstWindow(quotaId, currentTick, limiter);
    }
    return acquirePermitFast();
  }

  private boolean acquirePermitFast() {
    var remainingPermits = AVAILABLE_PERMITS_UPDATER.decrementAndGet(this);
    return remainingPermits >= 0;
  }

  private synchronized void updateBurstWindow(
      final String quotaId, final long currentTick, final RateLimiter limiter) {
    if (currentTick < burstWindowEndTick) {
      // Another thread must have updated the window while this one was waiting for the lock
      return;
    }
    final var quota = getQuota(quotaId, currentTick, this, limiter);
    setBurstWindowValues(quota, currentTick, 0, limiter);
  }

  private void setBurstWindowValues(
      final double quota,
      final long currentTick,
      final long baselinePermits,
      final RateLimiter limiter) {
    // Save these to ensure we use consistent values through the calculations
    final var burstWindowTicks = limiter.burstWindowTicks; // In case it ends up not being final
    final var burstWindowIndex = (currentTick - originTick) / burstWindowTicks;

    if (quota <= 0) {
      permitsForBurstWindow = 0;
    } else {
      // Start with an window number that will yield at least one
      // permit. Without this, if the quota allows for only a fractional
      // permit per burst window, the first request to acquire a
      // permit through a given State object will always fail.
      final var firstBurstWindowNumber =
          1 + calculateAverageTicksPerPermit(quota, limiter) / burstWindowTicks;
      final var burstWindowNumber = firstBurstWindowNumber + burstWindowIndex;

      // Using the difference between successive windows ensures that
      // fractional quotas will be meeted out properly, even if that
      // means that some burst windows get no permits.
      permitsForBurstWindow =
          (long) (quota * (double) burstWindowNumber)
              - (long) (quota * (double) (burstWindowNumber - 1));
    }
    if (baselinePermits == 0) {
      AVAILABLE_PERMITS_UPDATER.set(this, permitsForBurstWindow);
    } else {
      AVAILABLE_PERMITS_UPDATER.addAndGet(this, permitsForBurstWindow - baselinePermits);
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
        Long.max(limiter.burstWindowTicks, calculateAverageTicksPerPermit(quota, limiter)));
  }

  private static long calculateAverageTicksPerPermit(
      final double quota, final RateLimiter limiter) {
    if (quota <= 0.0) {
      throw new RuntimeException("quota is not positive");
    }
    return (long) Math.ceil((double) limiter.quotaWindowTicks / quota);
  }

  void quotaUpdated(final String quotaId, final double quota, final RateLimiter limiter) {
    retentionTicks = calculateRetentionTicks(quota, limiter);
    limiter.updateRetention(quotaId);
    synchronized (this) {
      // Adjust permits for current burst window
      setBurstWindowValues(
          quota, limiter.currentTickSupplier.get(), permitsForBurstWindow, limiter);
    }
  }
}
