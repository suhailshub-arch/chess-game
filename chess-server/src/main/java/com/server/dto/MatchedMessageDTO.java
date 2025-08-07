package com.server.dto;

public record MatchedMessageDTO(
    String whiteId, String whiteName,
    String blackId, String blackName,
    long gameId
) {}
