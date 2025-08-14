package com.server.util;

public record PauseInfo(long gameId, String disconnectedPlayerId, long pausedAtMillis, long deadlineMillis) {}
