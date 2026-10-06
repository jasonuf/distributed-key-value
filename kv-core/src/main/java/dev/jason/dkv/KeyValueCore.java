package dev.jason.dkv;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class KeyValueCore {

  private ConcurrentMap<String, String> map;

  public KeyValueCore() {
    this.map = new ConcurrentHashMap<>();
  }

  public Response apply(Command command) throws IllegalArgumentException {

    if (!Command.validateCommand(command)) {
      throw new IllegalArgumentException(
          "Your key value command is malformed, command=" + command.toString());
    }

    switch (command.type) {
      case Command.CommandType.GET -> {
        return applyGet(command);
      }
      case Command.CommandType.PUT -> {
        return applyPut(command);
      }
      case Command.CommandType.DELETE -> {
        return applyDelete(command);
      }
      default ->
          throw new IllegalArgumentException(
              "Your key value command is malformed, command=\"+command.toString()");
    }
  }

  private synchronized Response applyGet(Command command) {
    String key = command.args[0];
    if (!map.containsKey(key)) {
      return new Response(null, null);
    }

    return new Response(map.get(key), null);
  }

  private synchronized Response applyPut(Command command) {
    String key = command.args[0];
    String value = command.args[1];

    boolean present = map.containsKey(key);

    map.put(key, value);
    if (present) {
      return new Response(null, "present");
    } else {
      return new Response(null, "missing");
    }
  }

  private synchronized Response applyDelete(Command command) {
    String key = command.args[0];

    boolean present = map.containsKey(key);

    map.remove(key);
    if (present) {
      return new Response(null, "present");
    } else {
      return new Response(null, "missing");
    }
  }
}
