package com.shared.dto;

import com.shared.util.Colour;

public record ResumeOkDTO(long gameId, String fen, Colour toPlay, Colour yourColour, OpponentDTO opponent) {}