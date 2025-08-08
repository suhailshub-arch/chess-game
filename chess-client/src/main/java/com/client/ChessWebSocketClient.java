package com.client;

import java.net.URI;
import java.net.URISyntaxException;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shared.dto.Envelope;
import com.shared.dto.JoinMessageDTO;

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
