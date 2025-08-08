package com.shared.dto;

import com.shared.util.Colour;

public record MatchedMessageDTO(
    long gameId,
    String yourId,
    Colour colour,
    OpponentDTO opponent
) {}

