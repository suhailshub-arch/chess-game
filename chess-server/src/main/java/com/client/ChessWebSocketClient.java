package com.client;

import java.net.URI;
import java.net.URISyntaxException;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class ChessWebSocketClient extends WebSocketClient{

    private String PLAYERID_TEST;
    
    public ChessWebSocketClient(URI serverURI, String playerId){
        super(serverURI);
        this.PLAYERID_TEST = playerId;
    }

    @Override
    public void onOpen(ServerHandshake handshakeData){
        String message = "{ \"type\":\"join\", \"playerId\":\"" + PLAYERID_TEST + "\", \"name\":\"Alice\", \"rating\":1500 }";
        send(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote){
        System.out.println("closed with exit code " + code + " additional info: " + reason);
    }

    @Override
    public void onMessage(String message){
        System.out.println("Received message: " + message);
    }

    @Override
    public void onError(Exception ex){
        System.err.println("Error occured: " + ex.getMessage());
    }

    public static void main(String[] args) throws URISyntaxException, InterruptedException{
        String playerId = args[0];
        WebSocketClient client = new ChessWebSocketClient(new URI("ws://localhost:8080"), playerId);
        client.connect();
       
        Thread.currentThread().join();
    }
}
