package com.server.model;

public class Player {
    private String id;
    private int rating;

    public Player(String id, int rating){
        this.id = id;
        this.rating = rating;
    }

    public String getId(){
        return id;
    }

    public int getRating(){
        return rating;
    }

    @Override
    public String toString(){
        return "Player{ id=" + id + ", rating=" + rating + " }"; 
    }
}
