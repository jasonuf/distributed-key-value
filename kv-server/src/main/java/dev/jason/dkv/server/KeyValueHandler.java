package dev.jason.dkv.server;

import static dev.jason.dkv.server.HandlerUtil.send;
import static dev.jason.dkv.server.HandlerUtil.stripEnds;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.jason.dkv.Command;
import dev.jason.dkv.KeyValueCore;
import dev.jason.dkv.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class KeyValueHandler implements HttpHandler {

  private final KeyValueCore kvCore;

  public KeyValueHandler() {
    kvCore = new KeyValueCore();
  }

  private synchronized void handleGet(HttpExchange ex, String key) throws IOException {
    Command cmd = new Command(Command.CommandType.GET, new String[] {key});

    Response res = kvCore.apply(cmd);

    if (res.value() == null) {
      send(ex, 404, "Key not found in key-value store, key=" + key);
    } else {
      send(ex, 200, res.value());
    }
  }

  private synchronized void handlePut(HttpExchange ex, String key, String value)
      throws IOException {

    Command cmd = new Command(Command.CommandType.PUT, new String[] {key, value});
    Response res = kvCore.apply(cmd);

    if (res.previousState().equals("missing")) {
      send(ex, 201, "");
    } else {
      send(ex, 204, "");
    }
  }

  private synchronized void handleDelete(HttpExchange ex, String key) throws IOException {

    Command cmd = new Command(Command.CommandType.DELETE, new String[] {key});
    Response res = kvCore.apply(cmd);

    if (res.previousState().equals("present")) {
      send(ex, 204, "");
    } else {
      send(ex, 404, "Key not in key-value store, key=" + key);
    }
  }

  @Override
  public void handle(HttpExchange exchange) throws IOException {

    String method = exchange.getRequestMethod();
    String path = exchange.getRequestURI().getRawPath();
    path = stripEnds(path, '/');

    String[] pathList = path.split("/");

    for (int i = 0; i < pathList.length; ++i) {
      pathList[i] = URLDecoder.decode(pathList[i].replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    System.out.println("Request received with method=" + method + " path=" + path);

    if (!(method.equals("GET") || method.equals("PUT") || method.equals("DELETE"))) {
      send(exchange, 405, "Method Not Allowed");
    } else if (pathList.length == 1) {
      send(exchange, 400, "Path=" + path + "\nPlease include a key in your path: /kv/{key}");
    } else if (pathList.length != 2) {
      send(exchange, 404, "Path not found, path=" + path);
    } else {
      switch (method) {
        case "GET" -> {
          String key = pathList[1];
          handleGet(exchange, key);
        }
        case "PUT" -> {
          String key = pathList[1];
          String value;
          try (InputStream in = exchange.getRequestBody()) {
            value = new String(in.readAllBytes(), StandardCharsets.UTF_8);
          }

          handlePut(exchange, key, value);
        }
        case "DELETE" -> {
          String key = pathList[1];
          handleDelete(exchange, key);
        }

        default -> send(exchange, 400, "Something went wrong");
      }
    }
  }
}
