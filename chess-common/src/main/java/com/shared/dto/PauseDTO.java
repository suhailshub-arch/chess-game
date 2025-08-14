package com.shared.dto;

public record PauseDTO(long gameId, String disconnectedPlayerId, long resumeDeadlineMillis) {}