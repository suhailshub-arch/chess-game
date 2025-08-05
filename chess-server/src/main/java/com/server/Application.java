package com.server;

import com.server.model.Player;
import com.server.service.MatchmakingService;

public class Application {
    public static void main(String[] args) {
        MatchmakingService matchmakingService = new MatchmakingService();
        Player player1 = new Player("shub", 1500);
        
        matchmakingService.addPlayer(player1);
    }
}