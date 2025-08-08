package com.shared.dto;

public record Envelope<T>(String type, T payload) {}
