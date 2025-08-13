package com.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.server.model.ChessGame;
import com.server.model.Player;
import com.shared.util.GameResult;

public class ChessCLI {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Enter White's name: ");
        String whiteName = scanner.nextLine();
        System.out.println("Enter Black's name: ");
        String blackName = scanner.nextLine();

        Player[] players = { new Player("1", whiteName, 1000), new Player("2", blackName, 1500)};
        ChessGame game = new ChessGame(players, 1);

        while (game.getStatus() == ChessGame.STATUS.ONGOING) {
            String fenBoard = game.getPosition().toString();
            List<List<String>> board = fenToBoard(fenBoard);
            printBoard(board);

            int toPlay = game.getPosition().getToPlay();
            String colour = (toPlay == 0) ? "White" : "Black";
            System.out.println(colour + " (" + game.getPlayers()[toPlay].getId() + ") to move.");
            
            if(game.getPosition().isCheck()) {
                System.out.println("Check!");
            }

            System.out.print("Enter your move (e.g. e2e4 or e7e8q): ");
            String moveStr = scanner.nextLine();

            short move;
            try {
                move = game.parseMove(moveStr);
            } catch (Exception e) {
                System.out.println("Could not parse move: " + e.getMessage());
                continue;
            }

            game.makeMove(move);

            if (game.getPosition().isMate()) {
                fenBoard = game.getPosition().toString();
                board = fenToBoard(fenBoard);
                printBoard(board);
                System.out.println("Checkmate! " + colour + " loses.");
                game.setStatus(ChessGame.STATUS.FINISHED);
                game.setGameResult(toPlay == 0 ? GameResult.BLACK_WIN : GameResult.WHITE_WIN);
                break;
            }
            if (game.getPosition().isStaleMate()) {
                fenBoard = game.getPosition().toString();
                board = fenToBoard(fenBoard);
                printBoard(board);                
                System.out.println("Stalemate! The game is a draw.");
                game.setStatus(ChessGame.STATUS.FINISHED);
                game.setGameResult(GameResult.DRAW);
                break;
            }
            // game.setStatus(ChessGame.STATUS.FINISHED);
        }

        System.out.println("Game over. Result: " + game.getGameResult());

    }

    public static List<List<String>> fenToBoard(String fen) {
        List<List<String>> board = new ArrayList<>();
        // Only take the board part of the FEN string
        String[] rows = fen.split(" ")[0].split("/");

        for (String row : rows) {
            List<String> brow = new ArrayList<>();
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (Character.isDigit(c)) {
                    int empty = c - '0';
                    for (int j = 0; j < empty; j++) {
                        brow.add("--");
                    }
                } else if (c == 'p') {
                    brow.add("bp");
                } else if (c == 'P') {
                    brow.add("wp");
                } else if (Character.isLowerCase(c)) {
                    // black piece
                    brow.add("b" + Character.toUpperCase(c));
                } else {
                    // white piece
                    brow.add("w" + c);
                }
            }
            board.add(brow);
        }
        return board;
    }

    public static void printBoard(List<List<String>> board) {
    System.out.println("   a  b  c  d  e  f  g  h");
    int rowNum = 8;
    for (List<String> row : board) {
        System.out.print(rowNum + " ");
        for (String s : row) {
            System.out.print(s + " ");
        }
        System.out.println();
        rowNum--;
    }
}
}
