package org.example.server;

import io.netty.handler.codec.http.HttpResponseStatus;
import java.time.Duration;
import java.util.Map;
import org.example.ratelimiter.RateLimiter;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.NettyOutbound;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class Server {
  public static void main(final String[] args) {

    // Configuration that should be made external
    final String host = "localhost";
    final int port = 8080;

    final long burstIntervalNanos = Duration.ofSeconds(10).getNano();

    // This should be in an external database
    final Map<String, Double> rateLimitPerSecondByAccountId =
        Map.ofEntries(
            Map.entry("ANONYMOUS", 0.1),
            Map.entry("alice@example.com", 1.0),
            Map.entry("bob@example.com", 1.1),
            Map.entry("carol@example.com", 2.0),
            Map.entry("chuck@example.com", 10.0),
            Map.entry("craig@example.com", 100.0),
            Map.entry("dan@example.com", 1000.0),
            Map.entry("erin@example.com", 10000.0));

    final RateLimiter limiter =
        RateLimiter.newBuilder()
            .setMonotonicClock(System::nanoTime, Duration.ofNanos(1))
            .setBurstInterval(burstIntervalNanos)
            .setQuotaInterval(Duration.ofSeconds(1))
            .build(rateLimitPerSecondByAccountId::get));

    final DisposableServer server =
        HttpServer.create()
            .host(host)
            .port(port)
            .handle((request, response) -> handle(limiter, request, response))
            .bindNow();

    System.out.println("serving on " + host + ":" + port);
    server.onDispose().block();
  }

  private static NettyOutbound handle(
      final RateLimiter limiter,
      final HttpServerRequest request,
      final HttpServerResponse response) {

    // Simple but insecure way to encode the account ID.  In
    // production code it should be verified by or embedded into a
    // token with a digital signature valid for a limited time -- for
    // example a JWT (https://jwt.io/).
    final String accountId = request.requestHeaders().get("x-account-id", "ANONYMOUS");

    if (!limiter.acquirePermit(accountId)) {
      response.status(HttpResponseStatus.TOO_MANY_REQUESTS);
      return response.sendString(Mono.just("Exceeded quota"));
    }
    return response.sendString(Mono.just("Hello World!"));
  }
}
