package com.server.model;

public class Player {
    private String id;
    private int rating;
    private long joinTime;
    private String name;

    public Player(String id, String name, int rating){
        this.id = id;
        this.rating = rating;
        this.joinTime = System.currentTimeMillis();
        this.name = name;
    }

    public String getId(){
        return id;
    }

    public String getName(){
        return name;
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
