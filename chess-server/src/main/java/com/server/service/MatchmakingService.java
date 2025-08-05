package com.server.service;

import java.util.*;

import com.server.model.Player;

public class MatchmakingService {
    private final Map<String, Queue<Player>> buckets = new HashMap<>();

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
}
