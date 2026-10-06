package dev.jason.dkv;

import java.util.HashMap;
import java.util.Map;

public class KeyValueCore {

  private Map<String, String> map;

  public KeyValueCore() {
    this.map = new HashMap<>();
  }

  public Response apply(Command command) {

    switch (command) {
      case GetCommand cmd -> {
        return applyGet(cmd);
      }
      case PutCommand cmd -> {
        return applyPut(cmd);
      }
      case DeleteCommand cmd -> {
        return applyDelete(cmd);
      }
    }
  }

  private synchronized Response applyGet(GetCommand command) {
    String key = command.key();
    if (!map.containsKey(key)) {
      return new GetResponse(null, false);
    }

    return new GetResponse(map.get(key), true);
  }

  private synchronized Response applyPut(PutCommand command) {
    String key = command.key();
    String value = command.value();

    boolean present = map.containsKey(key);

    map.put(key, value);
    if (present) {
      return new PutResponse(true);
    } else {
      return new PutResponse(false);
    }
  }

  private synchronized Response applyDelete(DeleteCommand command) {
    String key = command.key();

    boolean present = map.containsKey(key);

    map.remove(key);
    if (present) {
      return new DeleteResponse(true);
    } else {
      return new DeleteResponse(false);
    }
  }
}
