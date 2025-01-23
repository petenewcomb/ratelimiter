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
    fetch(quotaId, state, limiter);
  }

  double get(final String quotaId, final long currentTick, final State state, final RateLimiter limiter) {
    if (isStale(currentTick, limiter)) {
      fetch(quotaId, state, limiter);
    }
    return value;
  }

  private boolean isStale(final long currentTick, final RateLimiter limiter) {
    return currentTick >= (lastFetchStartTick + limiter.quotaRefreshIntervalTicks);
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

  private synchronized void setQuota(final String quotaId, final Double quota, final Throwable e, final State state, final RateLimiter limiter) {
    this.fetchInProgress = false;
    final var currentTick = limiter.getCurrentTick();
    if (e != null) {
      limiter.recordQuotaFetchError.apply(quotaId, e);
    }
    this.quota = quota;
    state.quotaUpdated(quotaId, quota, limiter);
  }
}
