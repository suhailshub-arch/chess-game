package com.shared.dto;

import com.shared.util.GameOverReason;
import com.shared.util.GameResult;

public record GameOverDTO(long gameId, GameResult result, GameOverReason reason, String winnerId) {}
