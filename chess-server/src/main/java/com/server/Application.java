package com.server;

import com.server.model.Player;
import com.server.service.MatchmakingService;

public class Application {
    public static void main(String[] args) throws InterruptedException{
        MatchmakingService matchmakingService = new MatchmakingService();
        Player player1 = new Player("shub", 1500);
        Player player2 = new Player("keira", 1600);
        Player player3 = new Player("eden", 1400);

        matchmakingService.addPlayer(player1);
        matchmakingService.addPlayer(player2);
        matchmakingService.addPlayer(player3);

        matchmakingService.printQueues();

        matchmakingService.tryMatchWithWaiting();
        matchmakingService.printQueues();

        Thread.sleep(6_000);

        Player player4 = new Player("bob", 21);
        matchmakingService.addPlayer(player4);
        matchmakingService.printQueues();

        matchmakingService.tryMatchWithWaiting();
    }
}