package dev.jason.dkv;

import java.util.Arrays;

public class Command {
  public enum CommandType {
    GET,
    PUT,
    DELETE
  }

  public CommandType type;
  public String[] args;

  public Command(CommandType type, String[] args) {
    this.type = type;
    this.args = args;
  }

  public static boolean validateCommand(Command command) {
    if (command.type == null || command.args == null) {
      return false;
    }

    switch (command.type) {
      case CommandType.GET -> {
        return validateGet(command);
      }
      case CommandType.PUT -> {
        return validatePut(command);
      }
      case CommandType.DELETE -> {
        return validateDelete(command);
      }
      default -> {
        return false;
      }
    }
  }

  public static boolean validateGet(Command command) {
    if (command.args.length == 1 && command.args[0] != null && !command.args[0].isEmpty()) {
      return true;
    }

    return false;
  }

  public static boolean validatePut(Command command) {
    String[] args = command.args;

    if (args.length == 2 && args[0] != null && !args[0].isEmpty() && args[1] != null) {
      return true;
    }

    return false;
  }

  public static boolean validateDelete(Command command) {
    String[] args = command.args;

    if (args.length == 1 && args[0] != null && !args[0].isEmpty()) {
      return true;
    }

    return false;
  }

  @Override
  public String toString() {
    return "Command{" + "type=" + type + ", args=" + Arrays.toString(args) + '}';
  }
}
