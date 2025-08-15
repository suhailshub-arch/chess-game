package com.server;

// import com.server.model.Player;
import com.server.network.ChessWebSocketServer;
// import com.server.service.MatchmakingService;

import java.net.InetSocketAddress;

// import com.server.model.ChesspressoDemo;
public class Application {
    public static void main(String[] args) throws InterruptedException{
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

        String host = "0.0.0.0";

        InetSocketAddress address = new InetSocketAddress(host, port);
        ChessWebSocketServer server = new ChessWebSocketServer(address);
        server.start();
        // System.out.println("WebSocket server started on ws://localhost:8080");

        Thread.currentThread().join();
    }
}

