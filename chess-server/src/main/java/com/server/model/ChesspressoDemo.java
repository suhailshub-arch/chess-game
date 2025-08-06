package com.server.model;

import chesspresso.position.Position;
import chesspresso.Chess;
import chesspresso.game.Game;
import chesspresso.move.IllegalMoveException;
import chesspresso.move.Move;

public class ChesspressoDemo {
    public void play () {
        Game game = new Game();
        Position position = game.getPosition();

        // Print the initial board (FEN format)
        // System.out.println(position.toString());

        // // Let's make the move e2e4 (White pawn)
        // int fromSqi = Chess.E2;
        // int toSqi = Chess.E5;
        // short move = Move.getRegularMove(fromSqi, toSqi, false);

        // // Check legal via getAllMoves()
        // short[] legalMoves = position.getAllMoves();
        // boolean legal = false;
        // for (short m : legalMoves) {
        //     if (m == move) {
        //         legal = true;
        //         break;
        //     }
        // }

        // if (legal) {
        //     try {
        //         position.doMove(move);
        //         System.out.println("Made move successfuly");
        //     } catch (IllegalMoveException e) {
        //         System.out.println("Position invalid!");
        //     }
        // } else {
        //     System.out.println("Move is illegal!");
        // }
        System.out.println("HELLO");
        System.out.println(Chess.charToPiece('Q'));
    }
}
