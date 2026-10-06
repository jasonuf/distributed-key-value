package dev.jason.dkv;

public sealed interface Response permits GetResponse, PutResponse, DeleteResponse {}
