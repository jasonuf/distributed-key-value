package dev.jason.dkv;

public sealed interface Command permits GetCommand, PutCommand, DeleteCommand {}
