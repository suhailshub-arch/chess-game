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
import com.server.redis.RedisManager;
import com.server.service.MatchmakingService;
import com.server.util.Match;
import com.server.util.Pair;
import com.server.util.PauseInfo;

public class ChessWebSocketServer extends WebSocketServer{

    private static final int CONNECTION_LOST_TIMEOUT_SECONDS = 30;
    private static final long HEARTBEAT_INTERVAL_MS = 10_000L;
    private static final long HEARTBEAT_INITIAL_DELAY_MS = 2_000L;
    private static final long HEARTBEAT_TIMEOUT_MS = 30_000L;
    private static final long RECONNECT_GRACE_MS = 60_000L;

    private Map<WebSocket, Player> socketToPlayer;
    private Map<String, WebSocket> playerIdToSocket;
    private Map<WebSocket, ChessGame> socketToGame;
    private Map<Long, Pair<WebSocket, WebSocket>> gameIdToSockets;
    private Map<WebSocket, Long> lastSentTsByConn;
    private Map<WebSocket, Long> lastAckTsByConn;
    private final java.util.Map<Long, PauseInfo> pausedGames;

    private ObjectMapper objectMapper; 
    private MatchmakingService matchmakingService;

    private final java.util.concurrent.ScheduledExecutorService hbExec = 
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat-scheduler");
            t.setDaemon(true);
            return t;
        });
    
    
    public ChessWebSocketServer(InetSocketAddress address){
        super(address);
        this.socketToPlayer = new ConcurrentHashMap<>();
        this.playerIdToSocket = new ConcurrentHashMap<>();
        this.socketToGame = new ConcurrentHashMap<>();
        this.gameIdToSockets = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper();
        this.matchmakingService = new MatchmakingService(Integer.toString(getPort()));
        this.lastAckTsByConn = new ConcurrentHashMap<>();
        this.lastSentTsByConn = new ConcurrentHashMap<>();
        this.pausedGames = new ConcurrentHashMap<>();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake){
        // conn.send("Welcome to the server"); // Sends message to new client
        System.out.println("[SERVER " + getPort() + "] Connection opened from " + conn.getRemoteSocketAddress());
        long now = System.currentTimeMillis();
        lastAckTsByConn.put(conn, now);
        System.out.printf("[HB] seed alive %s at %d%n", socketLabel(conn), now);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote){
        System.out.println("Closed " + conn.getRemoteSocketAddress());
        lastAckTsByConn.remove(conn);

        Player player = socketToPlayer.get(conn);
        socketToPlayer.remove(conn);
        if (player != null) playerIdToSocket.remove(player.getId());

        ChessGame game = socketToGame.get(conn);
        if (game == null) {
            if (player != null) matchmakingService.removePlayerFromQueue(player);
            return;
        } else if (game != null) {
            // 1) Who left? Use the invariant: players[0] = WHITE, players[1] = BLACK
            final boolean leaverIsWhite = (player != null) && player.getId().equals(game.getPlayers()[0].getId());

            // 2) Find opponent socket + player (may be null if they also disconnected)
            Pair<WebSocket, WebSocket> pair = gameIdToSockets.get(game.getGameId());
            WebSocket oppSock = (pair != null && pair.first == conn) ? pair.second : (pair != null ? pair.first : null);

            // Player opponent = (oppSock != null) ? socketToPlayer.get(oppSock) : null;

            if(game.isEnded()){
                socketToGame.remove(conn);
                return;
            }
            socketToGame.remove(conn);

            if (pair != null) {
                if (leaverIsWhite) {
                    gameIdToSockets.put(game.getGameId(), new Pair<>(null, pair.second)); // white seat empty
                } else {
                    gameIdToSockets.put(game.getGameId(), new Pair<>(pair.first, null));  // black seat empty
                }
            }

            long now = System.currentTimeMillis();
            long deadline = now + RECONNECT_GRACE_MS;
            pausedGames.put(
                game.getGameId(),
                new PauseInfo(game.getGameId(), player.getId(), now, deadline)
            );
            System.out.printf("[PAUSE] game=%d by=%s until=%d%n", game.getGameId(), player.getId(), deadline);

            // Notify the opponent (if still connected)
            if (oppSock != null && oppSock.isOpen()) {
                PauseDTO pausePayload = new PauseDTO(game.getGameId(), player.getId(), deadline);
                try {
                    String json = objectMapper.writeValueAsString(new Envelope<>("pause", pausePayload));
                    safeSend(oppSock, json, socketLabel(oppSock));
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
                
            }

            return;
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

                for (var entry : new java.util.ArrayList<>(pausedGames.entrySet())) {
                    final long gameId = entry.getKey();
                    final PauseInfo info = entry.getValue();

                    if (info.disconnectedPlayerId().equals(joinMsg.playerId())) {
                        ChessGame game = matchmakingService.getActiveChessgame(gameId);
                        String playerId = info.disconnectedPlayerId();
                        boolean isWhite = playerId.equals(game.getPlayers()[0].getId());
                        Player returningPlayer = isWhite ? game.getPlayers()[0] : game.getPlayers()[1];
                        

                        Pair<WebSocket, WebSocket> pair = gameIdToSockets.get(gameId);
                        WebSocket oldSeat = (pair == null) ? null : (isWhite ? pair.first : pair.second);
                        if (oldSeat != null && oldSeat != conn && oldSeat.isOpen()) {
                            try { oldSeat.close(4001, "replaced by resume"); } catch (Exception ignore) {}
                        }

                        socketToPlayer.put(conn, returningPlayer);
                        playerIdToSocket.put(playerId, conn);
                        socketToGame.put(conn, game);

                        if (pair == null) {
                            gameIdToSockets.put(gameId, isWhite ? new Pair<>(conn, null) : new Pair<>(null, conn));
                        } else {
                            gameIdToSockets.put(gameId, isWhite ? new Pair<>(conn, pair.second) : new Pair<>(pair.first, conn));
                        }

                        Pair<WebSocket, WebSocket> after = gameIdToSockets.get(gameId);
                        boolean bothPresent = after != null
                                && after.first  != null && after.first.isOpen()
                                && after.second != null && after.second.isOpen();

                        if (bothPresent) {
                            pausedGames.remove(gameId);
                        }

                        String fen = game.getPosition().getFEN();
                        Colour toPlay = game.getCurrentPlayer().equals(game.getPlayers()[0]) ? Colour.WHITE : Colour.BLACK;
                        Player opponentPlayer  = isWhite ? game.getPlayers()[1] : game.getPlayers()[0];
                        OpponentDTO opp = new OpponentDTO(opponentPlayer.getId(), opponentPlayer.getName(), opponentPlayer.getRating());

                        ResumeOkDTO ok = new ResumeOkDTO(gameId, fen, toPlay, opp);
                        String jsonOk = objectMapper.writeValueAsString(new Envelope<>("resumeOk", ok));
                        safeSend(conn, jsonOk, socketLabel(conn));

                        WebSocket oppSock = isWhite ? after.second : after.first;
                        OpponentReconnectedDTO or = new OpponentReconnectedDTO(gameId, playerId);
                        String jsonOr = objectMapper.writeValueAsString(new Envelope<>("opponentReconnected", or));
                        safeSend(oppSock, jsonOr, socketLabel(oppSock));
                    }
                }
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

                if (isPaused(game.getGameId())) {
                    sendError(conn, "gamePaused", "Game is paused while opponent reconnects");
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
            if("heartbeat_ack".equals(messageType)){
                System.out.printf("[LOG] Heartbeat ACK from %s%n", socketLabel(conn));
                long ts = root.get("payload").get("ts").asLong();
                long now = System.currentTimeMillis();
                lastAckTsByConn.put(conn, now);
                if (lastSentTsByConn.get(conn) != null) {
                    long rtt = now - ts;
                    System.out.printf("[HB] ACK <- %s ts=%d rtt=%dms%n", socketLabel(conn), ts, rtt);
                }
            }
            if ("resume".equals(messageType)) {
                ResumeRequestDTO payload = objectMapper.treeToValue(root.get("payload"), ResumeRequestDTO.class);
                System.out.printf("[RESUME] request from %s for game %d%n", payload.playerId(), payload.gameId());
                long gameId = payload.gameId();
                String playerId = payload.playerId();
                PauseInfo info = pausedGames.get(gameId);
                long now = System.currentTimeMillis();

                if (info == null) {
                    sendError(conn, "resumeDenied", "Game not paused");
                    return;
                }

                if (now > info.deadlineMillis()) {
                    sendError(conn, "resumeDenied", "Deadline expired");
                    return;
                }

                ChessGame game = matchmakingService.getActiveChessgame(gameId);
                if (game == null) {
                    sendError(conn, "resumeDenied", "Game no longer active");
                    return;
                }

                boolean isWhiteId = playerId.equals(game.getPlayers()[0].getId());
                boolean isBlackId = playerId.equals(game.getPlayers()[1].getId());
                if (!(isWhiteId || isBlackId)) { sendError(conn, "resumeDenied", "Player not in game"); return; }

                // Only the paused/disconnected player can resume:
                if (!playerId.equals(info.disconnectedPlayerId())) {
                    sendError(conn, "resumeDenied", "Only the disconnected player can resume");
                    return;
                }

                boolean isWhite = playerId.equals(game.getPlayers()[0].getId());
                Player returningPlayer = isWhite ? game.getPlayers()[0] : game.getPlayers()[1];
                

                Pair<WebSocket, WebSocket> pair = gameIdToSockets.get(gameId);
                WebSocket oldSeat = (pair == null) ? null : (isWhite ? pair.first : pair.second);
                if (oldSeat != null && oldSeat != conn && oldSeat.isOpen()) {
                    try { oldSeat.close(4001, "replaced by resume"); } catch (Exception ignore) {}
                }

                socketToPlayer.put(conn, returningPlayer);
                playerIdToSocket.put(playerId, conn);
                socketToGame.put(conn, game);

                if (pair == null) {
                    gameIdToSockets.put(gameId, isWhite ? new Pair<>(conn, null) : new Pair<>(null, conn));
                } else {
                    gameIdToSockets.put(gameId, isWhite ? new Pair<>(conn, pair.second) : new Pair<>(pair.first, conn));
                }

                Pair<WebSocket, WebSocket> after = gameIdToSockets.get(gameId);
                boolean bothPresent = after != null
                        && after.first  != null && after.first.isOpen()
                        && after.second != null && after.second.isOpen();

                if (bothPresent) {
                    pausedGames.remove(gameId);
                }

                String fen = game.getPosition().getFEN();
                Colour toPlay = game.getCurrentPlayer().equals(game.getPlayers()[0]) ? Colour.WHITE : Colour.BLACK;
                Player opponentPlayer  = isWhite ? game.getPlayers()[1] : game.getPlayers()[0];
                OpponentDTO opp = new OpponentDTO(opponentPlayer.getId(), opponentPlayer.getName(), opponentPlayer.getRating());

                ResumeOkDTO ok = new ResumeOkDTO(gameId, fen, toPlay, opp);
                String jsonOk = objectMapper.writeValueAsString(new Envelope<>("resumeOk", ok));
                safeSend(conn, jsonOk, socketLabel(conn));

                WebSocket oppSock = isWhite ? after.second : after.first;
                OpponentReconnectedDTO or = new OpponentReconnectedDTO(gameId, playerId);
                String jsonOr = objectMapper.writeValueAsString(new Envelope<>("opponentReconnected", or));
                safeSend(oppSock, jsonOr, socketLabel(oppSock));
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
        System.out.println("Server started successfully on port " + this.getPort());
        setConnectionLostTimeout(CONNECTION_LOST_TIMEOUT_SECONDS);
        System.out.println("[HB] starting scheduler");
        hbExec.scheduleAtFixedRate(
            this::tickHeartbeats,
            HEARTBEAT_INITIAL_DELAY_MS,
            HEARTBEAT_INTERVAL_MS,
            java.util.concurrent.TimeUnit.MILLISECONDS
        );

    }

    private void finishGameSafely(long gameId, GameResult result, GameOverReason reason, String winnerId) {
        pausedGames.remove(gameId);
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

    private void tickHeartbeats() {
        try {
            System.out.println("[HB] tick");
            long now = System.currentTimeMillis();
            Iterator<WebSocket> connIterator = getConnections().iterator();
            while(connIterator.hasNext()){
                WebSocket conn = connIterator.next();
                if (conn != null && conn.isOpen()){
                    long ts = now;

                    Envelope<HeartbeatDTO> heartBeatEnvelope = new Envelope<>("heartbeat", new HeartbeatDTO(ts));
                    String json = objectMapper.writeValueAsString(heartBeatEnvelope);
                    safeSend(conn, json, socketLabel(conn));
                    lastSentTsByConn.put(conn, ts);
                    lastAckTsByConn.putIfAbsent(conn, now);
                    System.out.printf("[HB] -> %s ts=%d%n", socketLabel(conn), ts);
                }
                
            }

            for (WebSocket conn : getConnections()) {
                if (conn != null && conn.isOpen()) {
                    Long last = lastAckTsByConn.get(conn);
                    if (last != null) {
                        long silentFor = now - last;
                        if (silentFor > HEARTBEAT_TIMEOUT_MS) {
                            System.out.printf("[HB] timeout -> %s silent=%dms (closing)%n",
                                    socketLabel(conn), silentFor);
                            try { conn.close(4000, "heartbeat timeout"); } catch (Exception ignore) {}
                        }
                    }
                }
            }

            // --- hygiene: drop closed sockets from heartbeat maps ---
            lastAckTsByConn.keySet().removeIf(s -> s == null || !s.isOpen());
            lastSentTsByConn.keySet().removeIf(s -> s == null || !s.isOpen());
        } catch (Exception e) {
            System.err.println("[HB] tick error: " + e.getMessage());
        }

        final long now2 = System.currentTimeMillis();

        // Iterate over a snapshot so we can remove while iterating
        for (var entry : new java.util.ArrayList<>(pausedGames.entrySet())) {
            final long gameId = entry.getKey();
            final PauseInfo info = entry.getValue();

            if (now2 <= info.deadlineMillis()) continue; // still within grace

            // Prevent reprocessing if anything below throws or takes time
            pausedGames.remove(gameId);

            // Get authoritative game + sockets snapshot *before* finishing
            ChessGame game = matchmakingService.getActiveChessgame(gameId);
            Pair<WebSocket, WebSocket> pair = gameIdToSockets.get(gameId);

            // If game vanished (already cleaned elsewhere), nothing to do
            if (game == null) {
                System.out.printf("[PAUSE-EXPIRE] game=%d already gone%n", gameId);
                continue;
            }

            // If pair missing, we can't tell who is present → safest is draw
            if (pair == null) {
                System.out.printf("[PAUSE-EXPIRE] game=%d has no sockets; declaring draw%n", gameId);
                finishGameSafely(gameId, GameResult.DRAW, GameOverReason.ABANDON, null);
                continue;
            }

            boolean whitePresent = (pair.first != null && pair.first.isOpen());
            boolean blackPresent = (pair.second != null && pair.second.isOpen());

            GameResult result;
            String winnerId;

            if (whitePresent && !blackPresent) {
                result = GameResult.WHITE_WIN;
                winnerId = game.getPlayers()[0].getId(); // players[0] = WHITE
            } else if (blackPresent && !whitePresent) {
                result = GameResult.BLACK_WIN;
                winnerId = game.getPlayers()[1].getId(); // players[1] = BLACK
            } else if (!whitePresent && !blackPresent) {
                // nobody is here anymore
                result = GameResult.DRAW;
                winnerId = null;
            } else {
                // Both seats present yet game is still 'paused' → clean up the pause and continue
                System.out.printf("[PAUSE-EXPIRE] game=%d both seats present; clearing paused state%n", gameId);
                continue;
            }

            System.out.printf("[PAUSE-EXPIRE] game=%d deadline passed; result=%s winner=%s%n",
                    gameId, result, winnerId);
            finishGameSafely(gameId, result, GameOverReason.ABANDON, winnerId);
        }
    }

    public void stopHeartbeats() {
        hbExec.shutdownNow();
    }

    private boolean isPaused(long gameId) { return pausedGames.containsKey(gameId); }
    
    private PauseInfo getPause(long gameId) { return pausedGames.get(gameId); }
}

