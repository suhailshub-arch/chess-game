package com.server.service;

import java.util.*;

import com.server.model.ChessGame;
import com.server.model.Player;
import com.server.model.ChessGame.STATUS;
import com.server.redis.RedisManager;
import com.server.util.Match;
import com.shared.dto.Envelope;
import com.shared.dto.ErrorDTO;
import com.shared.util.GameResult;

public class MatchmakingService {
    private final Map<String, Queue<Player>> buckets = new HashMap<>();
    private final List<String> bucketOrder = Arrays.asList("low", "medium", "high");
    private final Map<Long, ChessGame> activeGames = new HashMap<>();
    private final static long WAIT_THRESHOLD_MS = 5_000;
    private long gameIdCounter = 1;
    private final String nodeId;
    private final java.util.concurrent.locks.ReentrantLock matchLock = new java.util.concurrent.locks.ReentrantLock();


    public MatchmakingService(String nodeId) {
        buckets.put("low", new LinkedList<>()); // 0-999
        buckets.put("medium", new LinkedList<>()); //1000-1999
        buckets.put("high", new LinkedList<>()); // > 2000
        this.nodeId = nodeId;
    }

    // Determine which bucket a player belongs in
    public String getBucket(int rating){
        if(rating < 1000) return "low";
        else if(rating < 2000) return "medium";
        else return "high";
    }

    // Add player to respective queue
    public void addPlayer(Player player){
        String bucket = getBucket(player.getRating());
        buckets.get(bucket).add(player);
        System.out.println(player + " added to bucket " + bucket);
    }

    public List<Match> tryMatchWithWaiting(){
        if (!matchLock.tryLock()) {
            System.out.println("[MATCH] busy; skip by " + Thread.currentThread().getName());
            return java.util.Collections.emptyList();
        }

        try {
            System.out.print(System.currentTimeMillis() + Thread.currentThread().getName());
            long currentTime;
            Set<String> matchedPlayers = new HashSet<>();
            List<Match> matches = new ArrayList<Match>();

            for(int i = 0; i < bucketOrder.size(); i++){
                String bucketKey = bucketOrder.get(i);
                Queue<Player> queue = buckets.get(bucketKey);

                int attempts = queue.size();
                // Iterator<Player> playerIterator = queue.iterator();
                while (attempts > 0 && !queue.isEmpty()) {
                    Player head = queue.peek();
                    if (head == null) break;
                    attempts--;

                    if(matchedPlayers.contains(head.getId())){
                        queue.add(queue.poll());
                        continue;
                    }
                    currentTime = System.currentTimeMillis();
                    long waited = currentTime - head.getJoinTime();
                    if (queue.size() < 2 && waited <= WAIT_THRESHOLD_MS) break;
                    if (queue.size() >= 2) {
                        Player player1 = queue.poll();
                        Player player2 = queue.poll();
                        CreateGameResult gameResult = createChessGame(player1, player2);
                        if (gameResult.ok()) {
                            matches.add(new Match(player1, player2, gameResult.game()));
                            matchedPlayers.add(player1.getId());
                            matchedPlayers.add(player2.getId());
                            System.out.println("Matched " + player1 + " vs " + player2);
                        } else {
                            System.out.println("[CREATE_FAIL] base " + player1.getId() + " vs " + player2.getId()
        + " reason=" + gameResult.error() + " msg=" + gameResult.reason());
                            queue.add(player1);
                            queue.add(player2);
                        }
                        
                        continue;
                    }

                    if(waited > WAIT_THRESHOLD_MS){
                        // Check previous queue
                        if(i > 0){
                            Queue<Player> prevQueue = buckets.get(bucketOrder.get(i - 1));
                            Player match = prevQueue.peek();

                            if(match != null){
                                match = prevQueue.poll();
                                Player player = queue.poll();
                                CreateGameResult gameResult = createChessGame(player, match);
                                if (gameResult.ok()) {
                                    matches.add(new Match(player, match, gameResult.game()));
                                    matchedPlayers.add(match.getId());
                                    matchedPlayers.add(player.getId());
                                    System.out.println("[Extended] Matched " + match + " vs " + player);
                                } else {
                                    System.out.println("[CREATE_FAIL] prev " + player.getId() + " vs " + match.getId()
        + " reason=" + gameResult.error() + " msg=" + gameResult.reason());
                                    prevQueue.add(match);
                                    queue.add(player);
                                }
                                
                                continue;
                            }

                        }

                        // Check next queue
                        if(i < bucketOrder.size() - 1){
                            Queue<Player> nextQueue = buckets.get(bucketOrder.get(i + 1));
                            Player match = nextQueue.peek();

                            if(match != null){
                                match = nextQueue.poll();
                                Player player = queue.poll();
                                CreateGameResult gameResult = createChessGame(player, match);
                                if (gameResult.ok()) {
                                    matches.add(new Match(player, match, gameResult.game()));
                                    matchedPlayers.add(match.getId());
                                    matchedPlayers.add(player.getId());
                                    System.out.println("[Extended] Matched " + match + " vs " + player);
                                } else {
                                    System.out.println("[CREATE_FAIL] next " + player.getId() + " vs " + match.getId()
        + " reason=" + gameResult.error() + " msg=" + gameResult.reason());
                                    nextQueue.add(match);
                                    queue.add(player);
                                }
                                
                                continue;
                            }
                        }
                        queue.add(queue.poll());
                        continue;
                    }
                }
            }
            return matches;
        } finally {
            matchLock.unlock();
        }
        
    }

    public CreateGameResult createChessGame(Player player1, Player player2){
        System.out.println("[CREATE] gid=" + gameIdCounter + " nodeId=" + nodeId
            + " p1=" + player1.getId() + " p2=" + player2.getId());
        java.util.Objects.requireNonNull(nodeId, "[CREATE] nodeId is null");


        Player[] players = {player1, player2};
        Long gid = gameIdCounter;
        ChessGame game = new ChessGame(players, gid);
        

        RedisManager rm = RedisManager.getInstance();
        boolean stateSet = rm.initGameState(game.getGameId(), this.nodeId, game.getPosition().getFEN(), player1.getId(), player2.getId());
        if (!stateSet) {
            return new CreateGameResult(false, null, CreateGameError.INIT_FAILED, "init failed");
        }

        boolean ok1 = rm.bindPlayerToGame(player1.getId(), game.getGameId());
        boolean ok2 = rm.bindPlayerToGame(player2.getId(), game.getGameId());

        if (!ok1 || !ok2) {
            System.out.printf("[Redis] bind failed p1=%s ok1=%s p2=%s ok2=%s%n",
                    player1.getId(), ok1, player2.getId(), ok2);
            return new CreateGameResult(false, null, CreateGameError.BIND_FAILED, "bind failed");
        }

        System.out.println("[Redis] Writing game " + game.getGameId() + " -> " + nodeId);
        activeGames.put(gid, game);
        gameIdCounter++;
        System.out.println("Game Created " + game.toString());
        return new CreateGameResult(true, game, null, null);
    }

    public void endGame(long gameId, GameResult gameResult) {
        ChessGame game = activeGames.get(gameId);
        if (game != null) {
            game.setStatus(STATUS.FINISHED);
            game.setGameResult(gameResult);
            activeGames.remove(gameId);
            System.out.println("Game " + game.toString() + " finished.");
        }
        
    }

    public void printQueues() {
        for (String bucket : bucketOrder) {
            System.out.println(bucket + ": " + buckets.get(bucket));
        }
    }

    public void printActiveGames() {
        for(ChessGame game : activeGames.values()){
            System.out.println(game.toString());
        }
    }

    public void removePlayerFromQueue(Player player){
        String bucket = getBucket(player.getRating());

        Queue<Player> queue = buckets.get(bucket);
        queue.remove(player);
    }

    public ChessGame getActiveChessgame(long gameId){
        return activeGames.get(gameId);
    }
}
