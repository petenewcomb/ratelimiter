package org.example.ratelimiter;

import java.util.function.Function;
import java.util.function.Supplier;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.concurrent.CompletableFuture;

class Quota {
  private volatile double value;
  private volatile long lastFetchStartTick = Long.MIN_VALUE;
  private boolean fetchInProgress;
  
  Quota(final String quotaId, final State state, final RateLimiter limiter) {
    this.value = limiter.failsafeQuota;
    System.out.printf("starting fetch for %s\n", quotaId);
    fetch(quotaId, state, limiter);
    System.out.printf("first fetch for %s started\n", quotaId);
  }

  double get(final String quotaId, final long currentTick, final State state, final RateLimiter limiter) {
    if (isStale(currentTick, limiter)) {
      fetch(quotaId, state, limiter);
    }
    return value;
  }

  private boolean isStale(final long currentTick, final RateLimiter limiter) {
    return currentTick >= (lastFetchStartTick + limiter.quotaRefreshPeriodTicks);
  }

  private synchronized void fetch(final String quotaId, final State state, final RateLimiter limiter) {
    final var currentTick = limiter.currentTickSupplier.get();
    if (fetchInProgress || !isStale(currentTick, limiter)) {
      // Another thread is updating or has updated the quota while this one waited for the lock
      return;
    }
    this.lastFetchStartTick = currentTick;
    this.fetchInProgress = true;
    try {
      limiter.getQuotaById.apply(quotaId).whenComplete((quota, e) -> setQuota(quotaId, quota, e, state, limiter));
    } catch (final RuntimeException e) {
      // Can't be sure that setQuota will be called.
      this.fetchInProgress = false;
      throw e;
    }
  }

  private synchronized void setQuota(final String quotaId, final Double value, final Throwable e, final State state, final RateLimiter limiter) {
    this.fetchInProgress = false;
    final var currentTick = limiter.currentTickSupplier.get();
    if (e != null) {
      limiter.quotaFetchErrorRecorder.accept(quotaId, e);
    }
    if (!Double.isNaN(value)) {
      this.value = value;
      state.quotaUpdated(quotaId, value, limiter);
    }
    System.out.printf("quota for %s is %f\n", quotaId, value);
  }
}
