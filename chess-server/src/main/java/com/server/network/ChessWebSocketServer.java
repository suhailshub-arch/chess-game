package com.server.network;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.model.ChessGame;
import com.server.model.Player;
import com.server.service.MatchmakingService;
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
    public void onMessage(WebSocket conn, String message){
        System.out.println("Received message from " + conn.getRemoteSocketAddress() + ": " + message);
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
