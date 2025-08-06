package com.server.service;

import java.util.*;

import com.server.model.ChessGame;
import com.server.model.Player;
import com.server.model.ChessGame.GAME_RESULT;
import com.server.model.ChessGame.STATUS;

public class MatchmakingService {
    private final Map<String, Queue<Player>> buckets = new HashMap<>();
    private final List<String> bucketOrder = Arrays.asList("low", "medium", "high");
    private final Map<Long, ChessGame> activeGames = new HashMap<>();
    private final static long WAIT_THRESHOLD_MS = 5_000;
    private long gameIdCounter = 1;

    public MatchmakingService() {
        buckets.put("low", new LinkedList<>()); // 0-999
        buckets.put("medium", new LinkedList<>()); //1000-1999
        buckets.put("high", new LinkedList<>()); // > 2000
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

    public void tryMatchWithWaiting(){
        long currentTime = System.currentTimeMillis();
        Set<String> matchedPlayers = new HashSet<>();

        for(int i = 0; i < bucketOrder.size(); i++){
            String bucketKey = bucketOrder.get(i);
            Queue<Player> queue = buckets.get(bucketKey);

            Iterator<Player> playerIterator = queue.iterator();
            while (playerIterator.hasNext()) {
                Player player = playerIterator.next();
                if(matchedPlayers.contains(player.getId())){
                    continue;
                }

                long waited = currentTime - player.getJoinTime();
                if (queue.size() >= 2) {
                    Player player1 = queue.poll();
                    Player player2 = queue.poll();
                    matchedPlayers.add(player1.getId());
                    matchedPlayers.add(player2.getId());
                    System.out.println("Matched " + player1 + " vs " + player2);
                    createChessGame(player1, player2);
                    continue;
                }

                if(waited > WAIT_THRESHOLD_MS){
                    // Check previous queue
                    if(i > 0){
                        Queue<Player> prevQueue = buckets.get(bucketOrder.get(i - 1));
                        Player match = prevQueue.poll();

                        if(match != null){
                            playerIterator.remove();
                            matchedPlayers.add(match.getId());
                            matchedPlayers.add(player.getId());
                            System.out.println("[Extended] Matched " + match + " vs " + player);
                            createChessGame(player, match);
                            continue;
                        }

                    }

                    // Check next queue
                    if(i < bucketOrder.size() - 1){
                        Queue<Player> nextQueue = buckets.get(bucketOrder.get(i + 1));
                        Player match = nextQueue.poll();

                        if(match != null){
                            playerIterator.remove();
                            matchedPlayers.add(match.getId());
                            matchedPlayers.add(player.getId());
                            System.out.println("[Extended] Matched " + match + " vs " + player);
                            createChessGame(player, match);
                            continue;
                        }
                    }
                }
            }
        }
    }

    public ChessGame createChessGame(Player player1, Player player2){
        Player[] players = {player1, player2};
        ChessGame game = new ChessGame(players, gameIdCounter);
        activeGames.put(gameIdCounter, game);
        gameIdCounter++;
        System.out.println("Game Created " + game.toString());
        return game;
    }

    public void endGame(long gameId, GAME_RESULT gameResult) {
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
}
