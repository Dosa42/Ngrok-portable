package com.ngrok.example;

import com.ngrok.Session;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;

public class Main {

    // This HTTP server is just for demonstration. If you already have an app
    // running, skip startServer() and point ngrok at its port instead.
    static void startServer() throws IOException {
        var server = HttpServer.create(new InetSocketAddress(8085), 0);
        server.createContext("/", exchange -> {
            System.out.printf("%s %s%n", exchange.getRequestMethod(), exchange.getRequestURI());
            var response = "Hello from ngrok-java!\n";
            exchange.sendResponseHeaders(200, response.length());
            try (var os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });
        server.start();
        System.out.println("Server listening on port 8085");
    }

    static void connectNgrok() throws IOException, InterruptedException {
        try (var session = Session.withAuthtokenFromEnv().connect()) {
            var forwarder = session.httpEndpoint()

                    // Uncomment below to use a specific domain.
                    // https://dashboard.ngrok.com/domains
                    // .domain("hello-world.your-domain.com")

                    // Uncomment below to load balance across multiple instances of your app.
                    // https://ngrok.com/docs/universal-gateway/endpoint-pooling/
                    // .poolingEnabled(true)

                    // Uncomment below to require visitors to log in with Google before accessing your app.
                    // https://ngrok.com/docs/traffic-policy/actions/oauth/
                    // .trafficPolicy("{\"on_http_request\":[{\"actions\":[{\"type\":\"oauth\",\"config\":{\"provider\":\"google\"}}]}]}")

                    .forward(new URL("http://localhost:8085"));
            System.out.println("Available at: " + forwarder.getUrl());
            Thread.currentThread().join();
        }
    }

    public static void main(String[] args) throws Exception {
        startServer();
        connectNgrok();
    }
}
