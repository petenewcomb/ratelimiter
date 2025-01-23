package org.example.server;


import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

public class Server {
    public static void main(final String[] args) {
        final DisposableServer server =
                HttpServer.create()
                .host("localhost") 
                .port(8080)        
                .handle((request, response) -> response.sendString(Mono.just("Hello World!")))
                .bindNow();

        System.out.println("server started");
        server.onDispose()
                .block();
    }
}
