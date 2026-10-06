package dev.jason.dkv.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jason.dkv.acceptance.support.KvClient;
import dev.jason.dkv.acceptance.support.KvClient.Response;
import dev.jason.dkv.acceptance.support.NodeProcess;
import dev.jason.dkv.acceptance.support.Stage;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Stage 1: GET, PUT and DELETE behave as docs/http-api.md specifies. */
@Stage(1)
class Stage01CrudIT {

  private static NodeProcess node;
  private static KvClient kv;

  @BeforeAll
  static void startNode() throws Exception {
    node = NodeProcess.start("stage01-crud");
    kv = node.client();
  }

  @AfterAll
  static void stopNode() throws Exception {
    if (node != null) {
      node.close();
    }
  }

  /** Every test uses its own keys, so tests sharing the node can't interfere. */
  private static String freshKey(String name) {
    return name + "-" + UUID.randomUUID();
  }

  @Test
  void getOfMissingKeyReturns404() {
    assertEquals(404, kv.get(freshKey("missing")).status(), "GET of a key never written");
  }

  @Test
  void putCreatesKeyWith201AndGetReturnsIt() {
    String key = freshKey("create");
    assertEquals(201, kv.put(key, "v1").status(), "PUT of a new key must report creation");

    Response get = kv.get(key);
    assertEquals(200, get.status(), "GET after PUT");
    assertEquals("v1", get.body(), "GET must return the value that was PUT");
  }

  @Test
  void getReturnsUtf8PlainText() {
    String key = freshKey("content-type");
    kv.put(key, "v");

    String contentType = kv.get(key).contentType().toLowerCase(Locale.ROOT).replace(" ", "");
    assertTrue(
        contentType.startsWith("text/plain") && contentType.contains("charset=utf-8"),
        "GET must answer with Content-Type: text/plain; charset=utf-8, got '" + contentType + "'");
  }

  @Test
  void putOfExistingKeyReplacesValueWith204() {
    String key = freshKey("replace");
    kv.put(key, "old");

    assertEquals(204, kv.put(key, "new").status(), "PUT of an existing key must report replace");
    assertEquals("new", kv.get(key).body(), "GET must return the latest value");
  }

  @Test
  void deleteOfExistingKeyReturns204AndKeyIsGone() {
    String key = freshKey("delete");
    kv.put(key, "v");

    assertEquals(204, kv.delete(key).status(), "DELETE of an existing key");
    assertEquals(404, kv.get(key).status(), "GET after DELETE must report the key missing");
  }

  @Test
  void deleteOfMissingKeyReturns404() {
    assertEquals(404, kv.delete(freshKey("delete-missing")).status(), "DELETE of a missing key");
  }

  @Test
  void keyCanBeRecreatedAfterDelete() {
    String key = freshKey("recreate");
    kv.put(key, "first");
    kv.delete(key);

    assertEquals(201, kv.put(key, "second").status(), "PUT after DELETE must report creation");
    assertEquals("second", kv.get(key).body());
  }

  @Test
  void emptyValueIsStoredAndIsNotTheSameAsMissing() {
    String key = freshKey("empty-value");
    assertEquals(201, kv.put(key, "").status(), "PUT with an empty body is a valid value");

    Response get = kv.get(key);
    assertEquals(200, get.status(), "a key holding the empty string exists");
    assertEquals("", get.body());
  }

  @Test
  void keysAndValuesAreUtf8AndPercentDecoded() {
    String prefix = freshKey("utf8");
    List<String> keys =
        List.of(
            prefix + " with spaces",
            prefix + "+plus",
            prefix + "/slash",
            prefix + "%percent",
            prefix + "-héllo-wörld",
            prefix + "-日本語",
            prefix + "-😀");
    for (String key : keys) {
      String value = "value for " + key + "\nsecond line, ünïcödé, 😀";
      assertEquals(
          201,
          kv.put(key, value).status(),
          "PUT of key '" + key + "' sent as " + KvClient.kvPath(key));
      Response get = kv.get(key);
      assertEquals(200, get.status(), "GET of key '" + key + "' sent as " + KvClient.kvPath(key));
      assertEquals(value, get.body(), "value of key '" + key + "' must round-trip as UTF-8");
    }
    assertEquals(
        404, kv.get(prefix + " with+spaces").status(), "'+' is a literal plus, not a space");
  }

  @Test
  void differentEncodingsOfTheSameKeyNameTheSameKey() {
    // "café-<uuid>" written three ways that RFC 3986 treats as identical: canonical, lowercase
    // hex, and an unreserved character ('c') needlessly percent-encoded.
    String suffix = UUID.randomUUID().toString();
    String canonical = "/kv/caf%C3%A9-" + suffix;
    String lowercaseHex = "/kv/caf%c3%a9-" + suffix;
    String encodedUnreserved = "/kv/%63af%C3%A9-" + suffix;

    assertEquals(201, kv.send("PUT", canonical, "v").status(), "PUT " + canonical);
    assertEquals(
        "v",
        kv.send("GET", lowercaseHex, null).body(),
        "GET " + lowercaseHex + " must find the key written as " + canonical);
    assertEquals(
        "v",
        kv.send("GET", encodedUnreserved, null).body(),
        "GET " + encodedUnreserved + " must find the key written as " + canonical);
    assertEquals(
        "v", kv.get("café-" + suffix).body(), "the stored key is the decoded string 'café-…'");
  }

  @Test
  void emptyKeyIsRejectedWith400() {
    assertEquals(400, kv.send("GET", "/kv/", null).status(), "GET /kv/ has no key");
    assertEquals(400, kv.send("PUT", "/kv/", "v").status(), "PUT /kv/ has no key");
    assertEquals(400, kv.send("DELETE", "/kv/", null).status(), "DELETE /kv/ has no key");
  }

  @Test
  void unsupportedMethodIsRejectedWith405() {
    String path = KvClient.kvPath(freshKey("method"));
    assertEquals(405, kv.send("POST", path, "v").status(), "POST is not part of the API");
    assertEquals(405, kv.send("PATCH", path, "v").status(), "PATCH is not part of the API");
  }

  @Test
  void pathOutsideKvIsNotFound() {
    assertEquals(404, kv.send("GET", "/nope", null).status(), "unknown route");
  }
}
