package dev.jason.dkv;

public record GetResponse(String value, boolean present) implements Response {}
