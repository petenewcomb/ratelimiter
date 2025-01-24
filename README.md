# Example Rate Limiter in Java

This project provides a rate limiting library and demonstrates its use within an HTTP server.  The core code is in [`RateLimiter.java`](ratelimiter/src/main/java/org/example/ratelimiter/RateLimiter.java) and the demonstration code is in [`Server.java`](server/src/main/java/org/example/server/Server.java).

## Design Principles and Requirements

* Must support per-user (or other requesting entity) quotas
* Must support multithreaded use without imposing a global synchronization point
* Must minimize latency overhead, especially for high throughput clients
* Should adapt to changing quotas

## Out of scope

* Global rate limiting across JVMs

## Design approach

At its core, this library implements the token bucket rate limiting pattern.  The token counter itself, the one that keeps track of available permits for a given quota ID, is an AtomicLong.  The "hot path" involves looking up the state object for the relevant quota ID in a Caffeine[1] cache, checking that the current monotonic time is still less than the volatile field that stores the end of the burst window for which the latest batch of tokens were released, and then decrementing and checking that the value of the AtomicLong was greater than zero to see if the request should be allowed.

If the time exceeds the burst window, the state object's mutex is locked and the AtomicLong is reset to the appropriate number of tokens for the now-current burst window given the quota for the ID.  The quota itself is fetched and occasionally re-fetched asynchronously so that governed requests need never be blocked due to a quota fetch.  This means that during cold-start for a given quota ID, the first and any other requests that come in before the quota fetch is complete are governed instead by a globally-configured "failsafe quota".  Users of the library can set that high if they are confident that the fetch will be quick, or low (even zero) if they want to limit their exposure, though that might mean rejecting valid requests until the quote fetch completes.  Library users also have the option to implement the async fetch synchronously, in which case those early requests will block instead of fail.  I imagine that the "failsafe quota" would be best set to a significant fraction of the request capacity of a given node or the max quota allowed for any ID.  The idea is to "fail open" so that if the quota fetch mechanism is failing, it doesn't lock clients out.  If the fetch mechanism fails after the first fetch, the previously-fetched (stale) quota for that ID continues to be used.

[1] [Caffeine](https://github.com/ben-manes/caffeine) is a well-known and excellent concurrent linked hash map implementation that manages entry lifetimes while providing lockless lookups of existing entries and that stripes mutations across multiple locks to reduce contention.

## Dependencies

* JDK version 21 or greater (some earlier versions might work)
* [wrk](https://github.com/wg/wrk) for running tests against a local server instance

## Building

```console
$ ./gradlew build
```

## Running

```console
$ ./gradlew -q --console=plain run
serving on localhost:8080
```

## Testing

```console
$ wrk -t6 -c100 -d10s -H 'X-Account-ID: alice@example.com' http://127.0.0.1:8080/
Running 10s test @ http://127.0.0.1:8080/
  6 threads and 100 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     1.07ms    6.73ms 140.17ms   99.07%
    Req/Sec    94.58k    42.78k  212.89k    73.23%
  5592175 requests in 10.03s, 271.99MB read
Requests/sec: 557822.50
Transfer/sec:     27.13MB
```
