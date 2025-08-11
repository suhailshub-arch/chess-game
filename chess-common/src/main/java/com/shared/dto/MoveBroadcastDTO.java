package com.shared.dto;

import com.shared.util.Colour;

public record MoveBroadcastDTO(long gameId, String uci, String fen, Colour toPlay) {}
