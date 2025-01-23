# Example Rate Limiter in Java

This project provides a rate limiting library and demonstrates its use within an HTTP server.  The core code is in [`RateLimiter.java`](ratelimiter/src/main/java/org/example/ratelimiter/RateLimiter.java) and the demonstration code is in [`Server.java`](server/src/main/java/org/example/server/Server.java).

## Design Principles and Requirements

* Must support per-user (or other requesting entity) quotas
* Must support multithreaded use without imposing a global synchronization point
* Must minimize latency overhead, especially for high throughput clients
* Should adapt to changing quotas

## Out of scope

* Global rate limiting across JVMs

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
