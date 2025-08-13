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
import com.shared.util.GameOverReason;
import com.shared.util.GameResult;

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
        System.out.println("new connection to " + conn.getRemoteSocketAddress());

    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote){
        System.out.println("Closed " + conn.getRemoteSocketAddress());

        Player player = socketToPlayer.get(conn);
        socketToPlayer.remove(conn);
        if (player != null) playerIdToSocket.remove(player.getId());

        ChessGame game = socketToGame.get(conn);
        if (game == null) {
            if (player != null) matchmakingService.removePlayerFromQueue(player);
            return;
        } else if (game != null) {
            // We’re here only if game != null
            // 1) Who left? Use the invariant: players[0] = WHITE, players[1] = BLACK
            final boolean leaverIsWhite = (player != null) && player.getId().equals(game.getPlayers()[0].getId());

            // 2) Find opponent socket + player (may be null if they also disconnected)
            Pair<WebSocket, WebSocket> pair = gameIdToSockets.get(game.getGameId());
            // NOTE: if your Pair uses getters, replace `.first`/`.second` with `getFirst()`/`getSecond()`
            WebSocket oppSock = (pair != null && pair.first == conn) ? pair.second : (pair != null ? pair.first : null);

            Player opponent = (oppSock != null) ? socketToPlayer.get(oppSock) : null;

            // 3) Compute winnerId + result, even if opponent is missing
            final String winnerId;
            final GameResult winnerResult;

            if (opponent != null) {
                // Winner is the remaining player; compute their color via players[0]/players[1]
                boolean opponentIsWhite = opponent.getId().equals(game.getPlayers()[0].getId());
                winnerId = opponent.getId();
                winnerResult = opponentIsWhite ? GameResult.WHITE_WIN : GameResult.BLACK_WIN;
            } else {
                // Fallback: opponent unknown (e.g., maps cleaned in a race). Use leaver’s color.
                winnerResult = leaverIsWhite ? GameResult.BLACK_WIN : GameResult.WHITE_WIN;
                winnerId     = leaverIsWhite ? game.getPlayers()[1].getId()
                                            : game.getPlayers()[0].getId();
            }

            // 4) End the game once, notify, and clean maps (your helper handles it)
            finishGameSafely(game.getGameId(), winnerResult, GameOverReason.ABANDON, winnerId);
        }
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
                Player mappedPlayer = socketToPlayer.get(conn);

                if (game == null || mappedPlayer == null) {
                    sendError(conn, "notInGame", "You are not currently in a game");
                    return;
                }
                if (game.isEnded()) {
                    sendError(conn, "gameAlreadyEnded", "This game has already ended");
                    return;
                }

                if (moveMsg.gameId() != game.getGameId()) {
                    sendError(conn, "wrongGameId", "Wrong game ID");
                    return;
                }

                if (!mappedPlayer.getId().equals(moveMsg.playerId())) {
                    sendError(conn, "playerIdMismatch", "Player ID does not match this connection");
                    return;
                }

                Player playerToMove = socketToPlayer.get(conn);
                if (!mappedPlayer.getId().equals(game.getCurrentPlayer().getId())) {
                    sendError(conn, "notYourTurn", "It is not your turn");
                    return;
                }

                short move = game.parseMove(moveMsg.uci());
                boolean makeMove = game.makeMove(move);

                if(makeMove){
                    if (game.getPosition().isMate()) {
                        String winnerId = playerToMove.getId();
                        GameResult result = playerToMove.equals(game.getPlayers()[0]) ? GameResult.WHITE_WIN : GameResult.BLACK_WIN;
                        
                        finishGameSafely(game.getGameId(), result, GameOverReason.CHECKMATE, winnerId);
                        return;
                    } else if (game.getPosition().isStaleMate()) {
                        finishGameSafely(game.getGameId(), GameResult.DRAW, GameOverReason.STALEMATE, null);
                        return;
                    }
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

    private void finishGameSafely(long gameId, GameResult result, GameOverReason reason, String winnerId) {
    // 1) Find the game
        ChessGame game = matchmakingService.getActiveChessgame(gameId);
        if (game == null) return;

        // We’ll snapshot everything we need while holding the lock,
        // then send over the network after releasing the lock.
        WebSocket whiteSock = null;
        WebSocket blackSock = null;
        String json = null;

        synchronized (game) {
            // 2) At-most-once guard
            if (!game.markEnded()) return;

            // 3) Mark/record end on the server side (idempotent on service side)
            matchmakingService.endGame(gameId, result);

            // 4) Snapshot sockets and clean maps while locked
            Pair<WebSocket, WebSocket> pair = gameIdToSockets.get(gameId);
            if (pair != null) {
                whiteSock = pair.first;
                blackSock = pair.second;

                dumpState("before", gameId);
                if (whiteSock != null) socketToGame.remove(whiteSock);
                if (blackSock != null) socketToGame.remove(blackSock);
                gameIdToSockets.remove(gameId);
                dumpState("after", gameId);
            }

            GameOverDTO payload = new GameOverDTO(gameId, result, reason, winnerId);
            Envelope<GameOverDTO> env = new Envelope<>("gameOver", payload);
            try {
                json = objectMapper.writeValueAsString(env);
            } catch (Exception e) {
                System.err.println("Failed to serialize gameOver: " + e.getMessage());
                // We still proceed to clean state; nothing left to send.
                json = null;
            }
        } // <-- release lock before network I/O

        if (json != null) {
            try { if (whiteSock != null) safeSend(whiteSock, json, socketLabel(whiteSock)); } catch (Exception e) {
                System.err.println("Send to white failed: " + e.getMessage());
            }
            try { if (blackSock != null) safeSend(blackSock, json, socketLabel(blackSock)); } catch (Exception e) {
                System.err.println("Send to black failed: " + e.getMessage());
            }
        }

        System.out.printf("game=%d ended reason=%s result=%s winner=%s%n",
                gameId, reason, result, winnerId);
    }

    private String socketLabel(WebSocket s) {
        if (s == null) return "null";
        try {
            Player p = socketToPlayer.get(s);
            if (p != null) return p.getId();
            return String.valueOf(s.getRemoteSocketAddress());
        } catch (Exception ignored) {
            return "unknown";
        }
    }


    private void dumpState(String tag, long gameId) {
        boolean hasGame = gameIdToSockets.containsKey(gameId);
        Pair<WebSocket, WebSocket> pair = gameIdToSockets.get(gameId);

        boolean whitePresent = false, blackPresent = false;
        String whiteLabel = "null", blackLabel = "null";

        if (pair != null) {
            WebSocket whiteSock = pair.first;
            WebSocket blackSock = pair.second;

            whitePresent = (whiteSock != null) && socketToGame.containsKey(whiteSock);
            blackPresent = (blackSock != null) && socketToGame.containsKey(blackSock);

            whiteLabel = socketLabel(whiteSock);
            blackLabel = socketLabel(blackSock);
        }

        System.out.printf(
            "[STATE] tag=%s game=%d hasGame=%s socketToGame.size=%d whitePresent=%s blackPresent=%s white=%s black=%s%n",
            tag, gameId, hasGame, socketToGame.size(), whitePresent, blackPresent, whiteLabel, blackLabel
        );
    }

    private void safeSend(WebSocket s, String json, String who) {
        if (s == null) return;
        try {
            if (s.isOpen()) {
                s.send(json);
            } else {
                System.out.println("Skip send to " + who + " (socket closed)");
            }
        } catch (Exception e) {
            System.out.println("Send to " + who + " failed: " + e.getMessage());
        }
    }

    private void sendError(WebSocket conn, String code, String message){
        Envelope<ErrorDTO> errorEnvelope = new Envelope<>("error", new ErrorDTO(code, message));
        try {
            String json = objectMapper.writeValueAsString(errorEnvelope);
            safeSend(conn, json, socketLabel(conn));
        } catch (Exception e) {
            System.err.println(e);
        }
        
    }
}

