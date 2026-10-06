package dev.jason.dkv.acceptance.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** A thin HTTP client for the routes in docs/http-api.md. Safe to share between threads. */
public final class KvClient {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  private final String baseUrl;
  private final HttpClient http;

  public KvClient(String baseUrl) {
    this.baseUrl = baseUrl;
    this.http =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(TIMEOUT)
            .build();
  }

  /** Status code, UTF-8 body, and Content-Type header (empty string if absent). */
  public record Response(int status, String body, String contentType) {
    @Override
    public String toString() {
      return status + (body.isEmpty() ? "" : " \"" + body + "\"");
    }
  }

  public Response get(String key) {
    return send("GET", kvPath(key), null);
  }

  public Response put(String key, String value) {
    return send("PUT", kvPath(key), value);
  }

  public Response delete(String key) {
    return send("DELETE", kvPath(key), null);
  }

  /** Sends any method to a raw, already-encoded path. For testing error handling. */
  public Response send(String method, String rawPath, String body) {
    HttpRequest.BodyPublisher publisher =
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl + rawPath))
            .timeout(TIMEOUT)
            .method(method, publisher)
            .build();
    try {
      HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
      return new Response(
          response.statusCode(),
          new String(response.body(), StandardCharsets.UTF_8),
          response.headers().firstValue("Content-Type").orElse(""));
    } catch (IOException e) {
      throw new AssertionError(method + " " + rawPath + " failed: " + e, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(method + " " + rawPath + " interrupted", e);
    }
  }

  public static String kvPath(String key) {
    return "/kv/" + encodeSegment(key);
  }

  /** RFC 3986 percent-encoding of one path segment: everything but unreserved characters. */
  public static String encodeSegment(String segment) {
    StringBuilder out = new StringBuilder();
    for (byte b : segment.getBytes(StandardCharsets.UTF_8)) {
      char c = (char) (b & 0xFF);
      if ((c >= 'A' && c <= 'Z')
          || (c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || c == '-'
          || c == '.'
          || c == '_'
          || c == '~') {
        out.append(c);
      } else {
        out.append('%').append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
      }
    }
    return out.toString();
  }
}
