package com.server.service;

import java.util.*;

import com.server.model.Player;

public class MatchmakingService {
    private final Map<String, Queue<Player>> buckets = new HashMap<>();
    private final List<String> bucketOrder = Arrays.asList("low", "medium", "high");
    private final static long WAIT_THRESHOLD_MS = 5_000;

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
        Set<Player> matchedPlayers = new HashSet<>();

        for(int i = 0; i < bucketOrder.size(); i++){
            String bucketKey = bucketOrder.get(i);
            Queue<Player> queue = buckets.get(bucketKey);

            Iterator<Player> playerIterator = queue.iterator();
            while (playerIterator.hasNext()) {
                Player player = playerIterator.next();
                if(matchedPlayers.contains(player)){
                    continue;
                }

                long waited = currentTime - player.getJoinTime();
                if (queue.size() >= 2) {
                    Player player1 = queue.poll();
                    Player player2 = queue.poll();
                    matchedPlayers.add(player1);
                    matchedPlayers.add(player2);
                    System.out.println("Matched " + player1 + " vs " + player2);
                    continue;
                }

                if(waited > WAIT_THRESHOLD_MS){
                    // Check previous queue
                    if(i > 0){
                        Queue<Player> prevQueue = buckets.get(bucketOrder.get(i - 1));
                        Player match = prevQueue.poll();

                        if(match != null){
                            playerIterator.remove();
                            matchedPlayers.add(match);
                            matchedPlayers.add(player);
                            System.out.println("[Extended] Matched " + match + " vs " + player);
                            continue;
                        }

                    }

                    // Check next queue
                    if(i < bucketOrder.size() - 1){
                        Queue<Player> nextQueue = buckets.get(bucketOrder.get(i + 1));
                        Player match = nextQueue.poll();

                        if(match != null){
                            playerIterator.remove();
                            matchedPlayers.add(match);
                            matchedPlayers.add(player);
                            System.out.println("[Extended] Matched " + match + " vs " + player);
                            continue;
                        }
                    }
                }
            }
        }
    }

    public void tryMatch(){
        for(String bucket: buckets.keySet()){
            Queue<Player> queue = buckets.get(bucket);
            while(queue.size() >= 2){
                Player player1 = queue.poll();
                Player player2 = queue.poll();
                System.out.println("Matched " + player1 + " vs " + player2);
            }
        }
    }

    public void printQueues() {
        for (String bucket : bucketOrder) {
            System.out.println(bucket + ": " + buckets.get(bucket));
        }
    }
}
