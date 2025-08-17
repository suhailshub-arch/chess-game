package com.server.redis;

import java.util.Set;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Transaction;

public class RedisManager {
    
    private static RedisManager instance;
    private JedisPool pool;

    private RedisManager() {
        this.pool = new JedisPool("127.0.0.1", 6380);
    }

    public static RedisManager getInstance() {
        if (instance == null) {
            instance = new RedisManager();
        }
        return instance;
    }

      /* ---------- Keys ---------- */
    private String kGameNode(long gid) { return "game:" + gid + ":node"; }
    private String kPlayerGame(String pid) { return "player:" + pid + ":game"; }
    private String kGamePlayers(long gid) { return "game:" + gid + ":players"; }
    private String kNodeGames(String nodeId) { return "node:" + nodeId + ":games"; }

    /* ---------- Game ↔ Node ---------- */
    public void setGameNode(long gameId, String nodeId) {
        try (Jedis j = pool.getResource()) {
        j.set(kGameNode(gameId), nodeId);
        j.sadd(kNodeGames(nodeId), String.valueOf(gameId));
        }
    }

    public String getGameNode(long gameId) {
        try (Jedis j = pool.getResource()) {
        return j.get(kGameNode(gameId));
        }
    }

    /* ---------- Player ↔ Game (safe) ---------- */
    /** Returns true if bound or already bound to same game; false if bound elsewhere. */
    public boolean bindPlayerToGame(String playerId, long gameId) {
        try (Jedis j = pool.getResource()) {
        String pKey = kPlayerGame(playerId);
        String gStr = String.valueOf(gameId);

        Long created = j.setnx(pKey, gStr);       // 1 if set, 0 if exists
        if (created == 1L) {
            j.sadd(kGamePlayers(gameId), playerId); // first bind
            return true;
        }
        String existing = j.get(pKey);
        if (gStr.equals(existing)) {
            j.sadd(kGamePlayers(gameId), playerId); // idempotent rejoin
            return true;
        }
        return false; // already in a different game
        }
    }

    public Long getPlayerGame(String playerId) {
        try (Jedis j = pool.getResource()) {
        String v = j.get(kPlayerGame(playerId));
        return v == null ? null : Long.parseLong(v);
        }
    }

    public void endGameCleanup(long gameId, String nodeId) {
        try (Jedis j = pool.getResource()) {
            Set<String> players = j.smembers(kGamePlayers(gameId));
            Transaction t = j.multi();
            t.srem(kNodeGames(nodeId), String.valueOf(gameId));
            t.del(kGameNode(gameId));
            for (String pid : players) t.del(kPlayerGame(pid));
            t.del(kGamePlayers(gameId));
            t.exec();
        }
    }

    public void close() {
        pool.close();
    }
}
