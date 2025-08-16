package com.server;

// import com.server.model.Player;
import com.server.network.ChessWebSocketServer;
import com.sun.net.httpserver.HttpExchange;
// import com.server.service.MatchmakingService;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

// import com.server.model.ChesspressoDemo;
public class Application {
    public static void main(String[] args) throws InterruptedException, IOException{
        // MatchmakingService matchmakingService = new MatchmakingService();
        // Player player1 = new Player("shub", 1500);
        // Player player2 = new Player("keira", 1600);
        // Player player3 = new Player("eden", 1400);

        // matchmakingService.addPlayer(player1);
        // matchmakingService.addPlayer(player2);
        // matchmakingService.addPlayer(player3);


        // matchmakingService.tryMatchWithWaiting();

        // Thread.sleep(6_000);

        // Player player4 = new Player("bob", 21);
        // matchmakingService.addPlayer(player4);

        // matchmakingService.tryMatchWithWaiting();
        // matchmakingService.printActiveGames();

        // ChesspressoDemo demo = new ChesspressoDemo();
        // demo.play();

        // InetSocketAddress address = new InetSocketAddress("localhost", 8080);
        int port = 8080;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (Exception ignore) {}
        }

        int healthPort = port + 1000;
        String host = "0.0.0.0";

        HttpServer healthServer = HttpServer.create(new InetSocketAddress(healthPort), 0);
        healthServer.createContext("/healthz", new HealthHandler());
        healthServer.setExecutor(null);
        healthServer.start();

        InetSocketAddress address = new InetSocketAddress(host, port);
        ChessWebSocketServer chessServer = new ChessWebSocketServer(address);
        chessServer.start();
        // System.out.println("WebSocket server started on ws://localhost:8080");

        Thread.currentThread().join();
    }

    static class HealthHandler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            String response = "OK\n";
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
}

