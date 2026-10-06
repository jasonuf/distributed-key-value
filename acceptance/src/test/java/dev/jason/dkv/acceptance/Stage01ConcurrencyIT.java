package dev.jason.dkv.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jason.dkv.acceptance.support.KvClient;
import dev.jason.dkv.acceptance.support.KvClient.Response;
import dev.jason.dkv.acceptance.support.NodeProcess;
import dev.jason.dkv.acceptance.support.Stage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Stage 1: many clients at once don't corrupt state. Each operation must behave as if it happened
 * alone, at a single instant.
 */
@Stage(1)
class Stage01ConcurrencyIT {

  private static final int CLIENTS = 32;
  private static final int ROUNDS = 40;

  private static NodeProcess node;
  private static KvClient kv;

  @BeforeAll
  static void startNode() throws Exception {
    node = NodeProcess.start("stage01-concurrency");
    kv = node.client();
  }

  @AfterAll
  static void stopNode() throws Exception {
    if (node != null) {
      node.close();
    }
  }

  @Test
  void concurrentWritersToDistinctKeysLoseNothing() throws Exception {
    String prefix = "distinct-" + UUID.randomUUID() + "-";
    int keysPerClient = 50;

    List<Response> creates =
        runConcurrently(
            CLIENTS,
            client -> {
              for (int i = 0; i < keysPerClient; i++) {
                Response r = kv.put(prefix + client + "-" + i, "value-" + client + "-" + i);
                if (r.status() != 201) {
                  return r;
                }
              }
              return new Response(201, "", "");
            });
    for (Response r : creates) {
      assertEquals(201, r.status(), "every PUT of a new, distinct key must report creation");
    }

    Map<String, String> wrong = new TreeMap<>();
    for (int client = 0; client < CLIENTS; client++) {
      for (int i = 0; i < keysPerClient; i++) {
        String key = prefix + client + "-" + i;
        Response get = kv.get(key);
        String expected = "value-" + client + "-" + i;
        if (get.status() != 200 || !get.body().equals(expected)) {
          wrong.put(key, get.toString());
        }
      }
    }
    assertTrue(
        wrong.isEmpty(),
        "acknowledged writes lost or corrupted under concurrent clients ("
            + wrong.size()
            + " of "
            + CLIENTS * keysPerClient
            + "): "
            + first(wrong, 10));
  }

  @Test
  void concurrentCreatesOfSameKeyReportExactlyOneCreation() throws Exception {
    for (int round = 0; round < ROUNDS; round++) {
      String key = "same-create-" + UUID.randomUUID();
      List<Response> puts = runConcurrently(CLIENTS, client -> kv.put(key, "writer-" + client));

      long created = puts.stream().filter(r -> r.status() == 201).count();
      long replaced = puts.stream().filter(r -> r.status() == 204).count();
      assertEquals(
          1,
          created,
          "round "
              + round
              + ": "
              + CLIENTS
              + " concurrent PUTs of a new key; exactly one must see it absent and report 201,"
              + " got "
              + created
              + " (statuses: "
              + statuses(puts)
              + ")");
      assertEquals(CLIENTS - 1, replaced, "round " + round + ": the others must report 204");

      Response get = kv.get(key);
      assertEquals(200, get.status(), "round " + round + ": key must exist after the PUTs");
      assertTrue(
          get.body().matches("writer-\\d+"),
          "round " + round + ": final value must be one that was written, got " + get);
    }
  }

  @Test
  void concurrentDeletesOfSameKeyReportExactlyOneDeletion() throws Exception {
    for (int round = 0; round < ROUNDS; round++) {
      String key = "same-delete-" + UUID.randomUUID();
      kv.put(key, "doomed");
      List<Response> deletes = runConcurrently(CLIENTS, client -> kv.delete(key));

      long deleted = deletes.stream().filter(r -> r.status() == 204).count();
      assertEquals(
          1,
          deleted,
          "round "
              + round
              + ": "
              + CLIENTS
              + " concurrent DELETEs of one key; exactly one must see it present and report 204,"
              + " got "
              + deleted
              + " (statuses: "
              + statuses(deletes)
              + ")");
      assertEquals(404, kv.get(key).status(), "round " + round + ": key must be gone");
    }
  }

  /** Runs one task per client, all released at the same instant, and returns their results. */
  private static List<Response> runConcurrently(int clients, IntFunction<Response> task)
      throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Response>> futures = new ArrayList<>();
    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int client = 0; client < clients; client++) {
        int id = client;
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return task.apply(id);
                }));
      }
      start.countDown();
      List<Response> results = new ArrayList<>();
      for (Future<Response> future : futures) {
        results.add(future.get());
      }
      return results;
    }
  }

  private static String statuses(List<Response> responses) {
    Map<Integer, Integer> counts = new TreeMap<>();
    for (Response r : responses) {
      counts.merge(r.status(), 1, Integer::sum);
    }
    return counts.toString();
  }

  private static String first(Map<String, String> map, int n) {
    StringBuilder out = new StringBuilder();
    map.entrySet().stream()
        .limit(n)
        .forEach(e -> out.append("\n  ").append(e.getKey()).append(" -> ").append(e.getValue()));
    return map.size() > n ? out.append("\n  ...").toString() : out.toString();
  }
}
