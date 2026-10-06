package dev.jason.dkv.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import dev.jason.dkv.acceptance.support.KvClient;
import dev.jason.dkv.acceptance.support.KvClient.Response;
import dev.jason.dkv.acceptance.support.NodeProcess;
import dev.jason.dkv.acceptance.support.Seeded;
import dev.jason.dkv.acceptance.support.Stage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Stage 1: the same sequence of commands, applied to two fresh nodes, gives the same results and
 * leaves the same state. This is the property replication depends on.
 */
@Stage(1)
class Stage01DeterminismIT {

  private static final int OPERATIONS = 2000;
  private static final List<String> KEYS =
      List.of(
          "a",
          "b",
          "c",
          "d",
          "e",
          "f",
          "g",
          "h",
          "user:1",
          "user:2",
          "with space",
          "plus+sign",
          "slash/key",
          "héllo",
          "日本",
          "😀",
          "x",
          "y",
          "z",
          "last");

  private record Op(String method, String key, String value) {
    Response sendTo(KvClient kv) {
      return switch (method) {
        case "GET" -> kv.get(key);
        case "PUT" -> kv.put(key, value);
        case "DELETE" -> kv.delete(key);
        default -> throw new IllegalStateException(method);
      };
    }

    @Override
    public String toString() {
      return method + " '" + key + "'" + (value == null ? "" : " = '" + value + "'");
    }
  }

  @Test
  @Seeded
  void sameCommandsOnTwoFreshNodesGiveSameResultsAndState(long seed) throws Exception {
    List<Op> ops = randomOps(new Random(seed));

    try (NodeProcess a = NodeProcess.start("stage01-determinism-a");
        NodeProcess b = NodeProcess.start("stage01-determinism-b")) {
      KvClient kvA = a.client();
      KvClient kvB = b.client();

      for (int i = 0; i < ops.size(); i++) {
        Op op = ops.get(i);
        Response ra = op.sendTo(kvA);
        Response rb = op.sendTo(kvB);
        if (ra.status() != rb.status() || !ra.body().equals(rb.body())) {
          fail(
              "nodes diverged at operation "
                  + i
                  + " ("
                  + op
                  + "): node A answered "
                  + ra
                  + ", node B answered "
                  + rb
                  + "\n  preceding operations: "
                  + ops.subList(Math.max(0, i - 5), i));
        }
      }

      for (String key : KEYS) {
        Response ra = kvA.get(key);
        Response rb = kvB.get(key);
        assertEquals(
            ra.toString(),
            rb.toString(),
            "final state differs for key '" + key + "' after " + OPERATIONS + " operations");
      }
    }
  }

  @Test
  @Seeded
  void randomCommandSequenceMatchesMapSemantics(long seed) throws Exception {
    List<Op> ops = randomOps(new Random(seed));
    Map<String, String> model = new HashMap<>();

    try (NodeProcess node = NodeProcess.start("stage01-model")) {
      KvClient kv = node.client();
      for (int i = 0; i < ops.size(); i++) {
        Op op = ops.get(i);
        Response expected = modelApply(model, op);
        Response actual = op.sendTo(kv);
        boolean bodyMatters = op.method().equals("GET") && expected.status() == 200;
        if (actual.status() != expected.status()
            || (bodyMatters && !actual.body().equals(expected.body()))) {
          fail(
              "operation "
                  + i
                  + " ("
                  + op
                  + "): expected "
                  + expected
                  + ", node answered "
                  + actual
                  + "\n  preceding operations: "
                  + ops.subList(Math.max(0, i - 5), i));
        }
      }
    }
  }

  /** The expected result of an operation, per docs/http-api.md. */
  private static Response modelApply(Map<String, String> model, Op op) {
    return switch (op.method()) {
      case "GET" ->
          model.containsKey(op.key())
              ? new Response(200, model.get(op.key()), "")
              : new Response(404, "", "");
      case "PUT" -> new Response(model.put(op.key(), op.value()) == null ? 201 : 204, "", "");
      case "DELETE" -> new Response(model.remove(op.key()) != null ? 204 : 404, "", "");
      default -> throw new IllegalStateException(op.method());
    };
  }

  private static List<Op> randomOps(Random random) {
    List<Op> ops = new ArrayList<>(OPERATIONS);
    for (int i = 0; i < OPERATIONS; i++) {
      String key = KEYS.get(random.nextInt(KEYS.size()));
      int roll = random.nextInt(100);
      if (roll < 35) {
        ops.add(new Op("GET", key, null));
      } else if (roll < 80) {
        ops.add(new Op("PUT", key, randomValue(random)));
      } else {
        ops.add(new Op("DELETE", key, null));
      }
    }
    return ops;
  }

  private static String randomValue(Random random) {
    return switch (random.nextInt(5)) {
      case 0 -> "";
      case 1 -> "ünïcödé-" + random.nextInt(1000);
      default -> "v" + random.nextInt(100_000);
    };
  }
}
