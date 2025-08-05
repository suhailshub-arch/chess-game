package com.server.model;

public class Player {
    private String id;
    private int rating;
    private long joinTime;

    public Player(String id, int rating){
        this.id = id;
        this.rating = rating;
        this.joinTime = System.currentTimeMillis();
    }

    public String getId(){
        return id;
    }

    public int getRating(){
        return rating;
    }

    public long getJoinTime(){
        return joinTime;
    }

    @Override
    public String toString(){
        return "Player{ id=" + id + ", rating=" + rating + " }"; 
    }
}
