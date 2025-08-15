package com.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shared.dto.Envelope;
import com.shared.dto.HeartbeatAckDTO;
import com.shared.dto.JoinMessageDTO;
import com.shared.dto.MoveBroadcastDTO;
import com.shared.dto.OpponentReconnectedDTO;
import com.shared.dto.PauseDTO;
import com.shared.dto.ResumeOkDTO;

public class ChessWebSocketClient extends WebSocketClient{

    private String PLAYERID_TEST;
    private ObjectMapper objectMapper;
    
    public ChessWebSocketClient(URI serverURI, String playerId){
        super(serverURI);
        this.PLAYERID_TEST = playerId;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void onOpen(ServerHandshake handshakeData){
        JoinMessageDTO joinMsg = new JoinMessageDTO(PLAYERID_TEST, "Alice", 1500);
        Envelope<JoinMessageDTO> joinEnvelope = new Envelope<>("join", joinMsg);
        try {
            String messageJson = objectMapper.writeValueAsString(joinEnvelope);
            send(messageJson);
        } catch (Exception e) {
            System.err.println(e);
        }
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String line = scanner.nextLine();
                send(line);
            }
        }).start();

    }

    @Override
    public void onClose(int code, String reason, boolean remote){
        System.out.println("closed with exit code " + code + " additional info: " + reason);
    }

    @Override
    public void onMessage(String message){
        // System.out.println("Received message: " + message);
        try {
            JsonNode root = objectMapper.readTree(message);
            String messageType = root.get("type").asText();

            if ("move".equals(messageType)) {
                MoveBroadcastDTO moveBroadcastDTO = objectMapper.treeToValue(root.get("payload"), MoveBroadcastDTO.class);
                List<List<String>> board = fenToBoard(moveBroadcastDTO.fen());
                printBoard(board);
            }
            if("heartbeat".equals(messageType)){
                long ts = root.get("payload").get("ts").asLong();
                Envelope<HeartbeatAckDTO> ackEnvelope = new Envelope<>("heartbeat_ack", new HeartbeatAckDTO(ts));
                String json = objectMapper.writeValueAsString(ackEnvelope);
                send(json);
                // System.out.printf("[LOG] Heartbeat from Server ts=%d%n", ts);
            }
            if ("pause".equals(messageType)) {
                PauseDTO pauseDTO = objectMapper.treeToValue(root.get("payload"), PauseDTO.class);
                System.out.printf("[LOG] Game %d paused by %s. Must resume by %d%n", pauseDTO.gameId(), pauseDTO.disconnectedPlayerId(), pauseDTO.resumeDeadlineMillis());
            }
            if ("resumeOk".equals(messageType)) {
                ResumeOkDTO resumeOkDTO = objectMapper.treeToValue(root.get("payload"), ResumeOkDTO.class);
                System.out.printf("[LOG] Game %d resumed%n", resumeOkDTO.gameId());
            }
            if ("opponentReconnected".equals(messageType)) {
                OpponentReconnectedDTO opponentReconnectedDTO = objectMapper.treeToValue(root.get("payload"), OpponentReconnectedDTO.class);
                System.out.printf("[LOG] Opponent %s reconnected to game %d%n", opponentReconnectedDTO.playerId(), opponentReconnectedDTO.gameId());
            }
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    @Override
    public void onError(Exception ex){
        System.err.println("Error occured: " + ex.getMessage());
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

    public static void main(String[] args) throws URISyntaxException, InterruptedException{
        String playerId = args[0];
        WebSocketClient client = new ChessWebSocketClient(new URI("ws://localhost:8080"), playerId);
        client.connect();
       
        Thread.currentThread().join();
    }
}
