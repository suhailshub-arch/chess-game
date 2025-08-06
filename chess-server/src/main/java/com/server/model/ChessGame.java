package com.server.model;

import java.util.List;

public class ChessGame {
    public enum GAME_RESULT {
        NOT_DECIDED,    
        PLAYER1_WIN,
        PLAYER2_WIN,
        DRAW,
        ABANDONED
    }
    public enum STATUS {
        ONGOING,
        FINISHED
    }
    private long gameId;
    private List<Player> players;
    private STATUS status;
    private GAME_RESULT gameResult;

    public ChessGame(List<Player> players, long gameId){
        this.players = players;
        this.gameId = gameId;
        this.status = STATUS.ONGOING;
        this.gameResult = GAME_RESULT.NOT_DECIDED;
    }

    public long getGameId(){
        return gameId;
    }

    public List<Player> getPlayers(){
        return players;
    }

    public STATUS getStatus(){
        return status;
    }

    public GAME_RESULT getGameResult(){
        return gameResult;
    }

    @Override
    public String toString(){
        return "Game ID: " + gameId + " " + players.get(0) + " vs " + players.get(1);
    }

}
