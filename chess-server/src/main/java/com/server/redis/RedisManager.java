package com.server.redis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private String kGameState(long gid) { return "game:" + gid + ":state"; }
    private String kGameMoves(long gid) { return "game:" + gid + ":moves"; }

    /* Commit a Move */
    public boolean commitMove (
        long gid, String nodeId, String newFen,
        String moveUci, String whiteId, String blackId,
        String turn, String status
    ) {
        long now = System.currentTimeMillis();
        try (Jedis j = pool.getResource()) {
            Transaction t = j.multi();
            Map<String,String> stateFieldsMap = new HashMap<>();
            stateFieldsMap.put("fen", newFen);
            stateFieldsMap.put("turn", turn);
            stateFieldsMap.put("status", status);
            stateFieldsMap.put("whiteId", whiteId);
            stateFieldsMap.put("blackId", blackId);
            stateFieldsMap.put("lastUpdated", Long.toString(now));
            t.hmset(kGameState(gid), stateFieldsMap);
            t.hincrBy(kGameState(gid), "version", 1);
            t.rpush(kGameMoves(gid), moveUci);
            t.set(kGameNode(gid), nodeId);
            t.sadd(kNodeGames(nodeId), String.valueOf(gid));
            List<Object> res = t.exec();
            return res != null;
        }  catch (Exception e) {
            System.out.println("[REDIS] Write failed");
            return false;
        }
    } 

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

    public boolean initGameState(long gid, String nodeId, String initialFen, String whiteId, String blackId) {
        try (Jedis j = pool.getResource()) {
            Map<String,String> initStateMap = new HashMap<>();

            if(j.exists(kGameState(gid))) {
                return true;
            }

            Transaction t = j.multi();
            String now = Long.toString(System.currentTimeMillis());

            

            t.del(kGameMoves(gid));

            initStateMap.put("fen", initialFen);
            initStateMap.put("turn", "w");
            initStateMap.put("status", "IN_PROGRESS");
            initStateMap.put("whiteId", whiteId);
            initStateMap.put("blackId", blackId);
            initStateMap.put("version", "0");
            initStateMap.put("lastUpdated", now);

            t.hmset(kGameState(gid), initStateMap);

            t.set(kGameNode(gid), nodeId);

            t.sadd(kNodeGames(nodeId), Long.toString(gid));

            List<Object> res = t.exec();
            return res != null;
        } catch (Exception e) {
            System.out.println("[INIT_FAIL] gid=" + gid + " node=" + nodeId + " class=" + e.getClass().getName()
                + " msg=" + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void cleanUpGameCreation(long gid, String nodeId, String whiteId, String blackId) {
        try (Jedis j = pool.getResource()) {
            j.del(kGameState(gid));
            j.del(kGameMoves(gid));
            j.del(kGameNode(gid));
            j.srem(kNodeGames(nodeId), Long.toString(gid));
            j.del(kPlayerGame(blackId));
            j.del(kPlayerGame(whiteId));
        }
    }

    public boolean endGamePersist(long gid, String nodeId, String resultString, String reasonString, String winnerIdOrNull) {
        try (Jedis j = pool.getResource()) {
            Map<String,String> endGameState = new HashMap<>();
            endGameState.put("status", "ENDED");
            endGameState.put("result", resultString);
            endGameState.put("reason", reasonString);
            endGameState.put("winnerId", winnerIdOrNull == null ? "" : winnerIdOrNull);
            endGameState.put("lastUpdated", Long.toString(System.currentTimeMillis()));
            Transaction t = j.multi();
            t.hmset(kGameState(gid), endGameState);
            t.del(kGameNode(gid));
            t.srem(kNodeGames(nodeId), Long.toString(gid));

            List<Object> res = t.exec();
            return res != null;

        }
    }
}
