package com.client;

import java.util.ArrayList;
import java.util.List;

public class Board {
    
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
