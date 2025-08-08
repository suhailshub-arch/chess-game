package com.server.network;

import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.dto.JoinMessageDTO;
import com.server.dto.MatchedMessageDTO;
import com.server.model.ChessGame;
import com.server.model.Player;
import com.server.service.MatchmakingService;
import com.server.util.Match;
import com.server.util.Pair;

public class ChessWebSocketServer extends WebSocketServer{

    private Map<WebSocket, Player> socketToPlayer;
    private Map<String, WebSocket> playerIdToSocket;
    private Map<WebSocket, ChessGame> socketToGame;
    private Map<Long, Pair<WebSocket, WebSocket>> gameIdToSockets;
    private ObjectMapper objectMapper; 
    private MatchmakingService matchmakingService;
    private static final int CONNECTION_LOST_TIMEOUT_SECONDS = 30;
    
    public ChessWebSocketServer(InetSocketAddress address){
        super(address);
        this.socketToPlayer = new ConcurrentHashMap<>();
        this.playerIdToSocket = new ConcurrentHashMap<>();
        this.socketToGame = new ConcurrentHashMap<>();
        this.gameIdToSockets = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper();
        this.matchmakingService = new MatchmakingService();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake){
        conn.send("Welcome to the server"); // Sends message to new client
        broadcast("new connection: " + handshake.getResourceDescriptor());
        System.out.println("new connection to " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote){
        System.out.println("CLosed " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("Received message from " + conn.getRemoteSocketAddress() + ": " + message);
        try {
            JsonNode jsonMessage = objectMapper.readTree(message);
            JsonNode messageType = jsonMessage.get("type");
            
            if (messageType.textValue().equals("join")) {
                JoinMessageDTO joinMsg = objectMapper.readValue(message, JoinMessageDTO.class);
                Player player = new Player(joinMsg.playerId(), joinMsg.name(), joinMsg.rating());
                socketToPlayer.put(conn, player);
                playerIdToSocket.put(joinMsg.playerId(), conn);
                matchmakingService.addPlayer(player);

                List<Match> matches = matchmakingService.tryMatchWithWaiting();
                if (!matches.isEmpty()) {
                    Iterator<Match> itMatches = matches.iterator();
                    while(itMatches.hasNext()){
                        Match match = itMatches.next();
                        ChessGame game = match.game;
                        Player playerWhite = match.white;
                        Player playerBlack = match.black;
                        WebSocket playerWhiteConn = playerIdToSocket.get(playerWhite.getId());
                        WebSocket playerBlackConn = playerIdToSocket.get(playerBlack.getId());
                        socketToGame.put(conn, game);
                        gameIdToSockets.put(game.getGameId(), new Pair<>(playerWhiteConn, playerBlackConn));

                        MatchedMessageDTO matchedMessage = new MatchedMessageDTO(playerWhite.getId(), playerWhite.getName(), playerBlack.getId(), playerBlack.getName(), game.getGameId());
                        playerWhiteConn.send(objectMapper.writeValueAsString(matchedMessage));
                        playerBlackConn.send(objectMapper.writeValueAsString(matchedMessage));

                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }

    
    }

    @Override
    public void onError(WebSocket conn, Exception ex){
        System.err.println("An error occured on connection " + conn.getRemoteSocketAddress() + ": " + ex);
    }

    @Override
    public void onStart(){
        System.out.println("Server started successfully");
        setConnectionLostTimeout(CONNECTION_LOST_TIMEOUT_SECONDS);
    }

    
}
