package com.server;

import com.server.model.Player;

public class Application {
    public static void main(String[] args) {
        Player player1 = new Player("shub", 1500);
        System.out.println(player1.toString());
    }
}