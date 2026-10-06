package dev.jason.dkv;

public record DeleteCommand(String key) implements Command {}
