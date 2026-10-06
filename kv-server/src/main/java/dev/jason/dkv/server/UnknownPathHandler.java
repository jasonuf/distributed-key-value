package dev.jason.dkv.server;

import static dev.jason.dkv.server.HandlerUtil.send;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;

public class UnknownPathHandler implements HttpHandler {

  @Override
  public void handle(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    send(exchange, 404, "Path not found, path=" + path);
  }
}
