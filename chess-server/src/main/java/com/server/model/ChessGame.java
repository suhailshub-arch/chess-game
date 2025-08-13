package com.server.model;

import com.shared.util.GameResult;

import chesspresso.Chess;
import chesspresso.move.IllegalMoveException;
import chesspresso.move.Move;
import chesspresso.position.Position;

public class ChessGame {
    public enum STATUS {
        ONGOING,
        FINISHED
    }
    private long gameId;
    private Player[] players;
    private STATUS status;
    private GameResult gameResult;
    private Position position;
    private Player currentPlayer;
    private final java.util.concurrent.atomic.AtomicBoolean ended = new java.util.concurrent.atomic.AtomicBoolean(false);

    public ChessGame(Player[] players, long gameId){
        this.players = players;
        this.gameId = gameId;
        this.status = STATUS.ONGOING;
        this.position = Position.createInitialPosition();
        this.currentPlayer = players[0];
    }

    public long getGameId(){
        return gameId;
    }

    public Player[] getPlayers(){
        return players;
    }

    public STATUS getStatus(){
        return status;
    }

    public GameResult getGameResult(){
        return gameResult;
    }

    public Position getPosition(){
        return position;
    }

    public Player getCurrentPlayer(){
        return currentPlayer;
    }

    public void setStatus(STATUS status){
        this.status = status;
    }

    public void setGameResult(GameResult gameResult) {
        this.gameResult = gameResult;
    }

    @Override
    public String toString(){
        return "Game ID: " + gameId + " " + players[0].toString() + " vs " + players[1].toString();
    }

    public short parseMove(String moveStr) {
        
        int fromSqi = Chess.strToSqi(moveStr.substring(0, 2));
        int toSqi = Chess.strToSqi(moveStr.substring(2, 4));
        int startSquareStone = this.position.getStone(fromSqi);
        int endSquareStone = this.position.getStone(toSqi);

        // Is there a piece at the target, and is it NOT the same color as the mover?
        boolean isCapture = (endSquareStone != Chess.NO_STONE) &&
            !Chess.stoneHasColor(endSquareStone, Chess.stoneToColor(startSquareStone));
        
        if(moveStr.length() == 5){
            char promotionPieceChar = Character.toUpperCase(moveStr.charAt(4));
            int promotionPiece = Chess.charToPiece(promotionPieceChar);
            return Move.getPawnMove(fromSqi, toSqi, isCapture, promotionPiece);
        }
        return Move.getRegularMove(fromSqi, toSqi, isCapture);
    }

    public boolean makeMove(short move){
        short[] legalMoves = this.position.getAllMoves();
        boolean legal = false;
        for (short m : legalMoves) {
            if (m == move) {
                legal = true;
                break;
            }
        }

        if (legal) {
            try {
                this.position.doMove(move);
                System.out.println("Made move successfuly");
                currentPlayer = players[position.getToPlay()];
                return true;
            } catch (IllegalMoveException e) {
                System.out.println("Illegal Move!");
                return false;
            }
        } else {
            System.out.println("Move is illegal!");
            return false;
        }
    }
    
    public boolean markEnded() { return ended.compareAndSet(false, true); }

    public boolean isEnded() { return ended.get(); }

}
