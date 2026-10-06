package dev.jason.dkv.server;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class HandlerUtil {
  static void send(HttpExchange ex, int status, String text) throws IOException {
    ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    ex.sendResponseHeaders(status, bytes.length);
    try (OutputStream os = ex.getResponseBody()) {
      os.write(bytes);
    }
  }

  static String stripEnds(String s, Character match) {
    if (s == null || s.equals("")) {
      return s;
    }

    int l = 0;
    int r = s.length() - 1;

    while (l < r && s.charAt(l) == match) {
      l += 1;
    }

    while (r > l && s.charAt(r) == match) {
      r -= 1;
    }

    return s.substring(l, r + 1);
  }
}
