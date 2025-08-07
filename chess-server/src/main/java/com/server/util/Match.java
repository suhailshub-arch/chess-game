package com.server.util;

import com.server.model.ChessGame;
import com.server.model.Player;

public class Match {
    public Player white;
    public Player black;
    public ChessGame game;

    public Match(Player white, Player black, ChessGame game) {
        this.white = white;
        this.black = black;
        this.game = game;
    }
}
