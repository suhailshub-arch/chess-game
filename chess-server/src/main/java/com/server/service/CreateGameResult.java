package com.server.service;

import com.server.model.ChessGame;

public record CreateGameResult(boolean ok, ChessGame game, CreateGameError error, String reason) {}
