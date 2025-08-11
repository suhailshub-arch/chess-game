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
import com.shared.dto.*;
import com.shared.util.Colour;

import chesspresso.position.Position;

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
        System.out.println("Closed " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) { 
        System.out.println("Received message from " + conn.getRemoteSocketAddress() + ": " + message);
        try {
            JsonNode root = objectMapper.readTree(message);
            String messageType = root.get("type").asText();
            
            if ("join".equals(messageType)) {
                JoinMessageDTO joinMsg = objectMapper.treeToValue(root.get("payload"), JoinMessageDTO.class);
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
                        socketToGame.put(playerWhiteConn, game);
                        socketToGame.put(playerBlackConn, game);
                        gameIdToSockets.put(game.getGameId(), new Pair<>(playerWhiteConn, playerBlackConn));

                        String initialFen = Position.createInitialPosition().getFEN();

                        MatchedMessageDTO whiteMatchedMessage = new MatchedMessageDTO(game.getGameId(), playerWhite.getId(), Colour.WHITE, new OpponentDTO(playerBlack.getId(), playerBlack.getName(), playerBlack.getRating()), initialFen);
                        MatchedMessageDTO blackMatchedMessage = new MatchedMessageDTO(game.getGameId(), playerBlack.getId(), Colour.BLACK, new OpponentDTO(playerWhite.getId(), playerWhite.getName(), playerWhite.getRating()), initialFen);
                        playerWhiteConn.send(objectMapper.writeValueAsString(new Envelope<MatchedMessageDTO>("matchFound", whiteMatchedMessage)));
                        playerBlackConn.send(objectMapper.writeValueAsString(new Envelope<MatchedMessageDTO>("matchFound", blackMatchedMessage)));

                    }
                }
            }
            if ("move".equals(messageType)) {
                MoveMessageDTO moveMsg = objectMapper.treeToValue(root.get("payload"), MoveMessageDTO.class);
                ChessGame game = socketToGame.get(conn);
                if (game == null) {
                    System.err.println("Game not found");
                    return;
                }

                Player playerToMove = socketToPlayer.get(conn);
                if (!playerToMove.getId().equals(game.getCurrentPlayer().getId())) {
                    System.err.println("Not your turn");
                    return;
                } 

                short move = game.parseMove(moveMsg.uci());
                boolean makeMove = game.makeMove(move);

                if(makeMove){
                    String newFen = game.getPosition().getFEN();
                    Player currentPlayer = game.getCurrentPlayer();
                    // TODO: ADD COLOUR TO PLAYER MODEL!!!!!!!!!!!!!!!
                    Colour toPlay = currentPlayer.equals(game.getPlayers()[0]) ? Colour.WHITE : Colour.BLACK; 
                    MoveBroadcastDTO broadcastMsg = new MoveBroadcastDTO(game.getGameId(), moveMsg.uci(), newFen, toPlay);
                    Envelope<MoveBroadcastDTO> moveEnvelope = new Envelope<>("move", broadcastMsg);
                    String json = objectMapper.writeValueAsString(moveEnvelope);

                    Pair<WebSocket, WebSocket> sockets = gameIdToSockets.get(game.getGameId());
                    sockets.first.send(json);
                    sockets.second.send(json);
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
