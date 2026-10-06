package dev.jason.dkv;

public record PutCommand(String key, String value) implements Command {}
