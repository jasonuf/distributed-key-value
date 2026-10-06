package dev.jason.dkv.acceptance.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A dkv node running as a separate JVM process, started the way docs/http-api.md describes. Output
 * goes to {@code acceptance/target/node-logs/}.
 */
public final class NodeProcess implements AutoCloseable {

  public static final String MAIN_CLASS = "dev.jason.dkv.server.Main";

  /** Set by acceptance/pom.xml: kv-server and its runtime dependencies. */
  static final String CLASSPATH_PROPERTY = "dkv.node.classpath";

  private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(15);
  private static final Path LOG_DIR = Path.of("target", "node-logs");

  private final String name;
  private final Process process;
  private final int port;
  private final Path log;

  private NodeProcess(String name, Process process, int port, Path log) {
    this.name = name;
    this.process = process;
    this.port = port;
    this.log = log;
  }

  /** Starts a node on a free port and waits until it accepts connections. */
  public static NodeProcess start(String name) throws IOException, InterruptedException {
    int port = freePort();
    Files.createDirectories(LOG_DIR);
    Path log = LOG_DIR.resolve(name + "-" + port + ".log");

    List<String> command = new ArrayList<>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    command.add("-Xmx256m");
    command.add("-cp");
    command.add(classpath());
    command.add(MAIN_CLASS);
    command.add("--port");
    command.add(Integer.toString(port));

    Process process =
        new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    NodeProcess node = new NodeProcess(name, process, port, log);
    node.awaitListening();
    return node;
  }

  public int port() {
    return port;
  }

  public String baseUrl() {
    return "http://127.0.0.1:" + port;
  }

  public KvClient client() {
    return new KvClient(baseUrl());
  }

  /** Stops the node with SIGTERM, escalating to SIGKILL if it doesn't exit. */
  @Override
  public void close() throws InterruptedException {
    process.destroy();
    if (!process.waitFor(5, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      process.waitFor(5, TimeUnit.SECONDS);
    }
  }

  private void awaitListening() throws InterruptedException {
    long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      if (!process.isAlive()) {
        throw new IllegalStateException(
            "node "
                + name
                + " exited with code "
                + process.exitValue()
                + " before listening on port "
                + port
                + missingMainClassHint()
                + "\n--- "
                + log
                + " ---\n"
                + logTail());
      }
      try (Socket socket = new Socket()) {
        socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
        return;
      } catch (IOException notYet) {
        Thread.sleep(50);
      }
    }
    close();
    throw new IllegalStateException(
        "node "
            + name
            + " did not accept connections on 127.0.0.1:"
            + port
            + " within "
            + STARTUP_TIMEOUT.toSeconds()
            + "s\n--- "
            + log
            + " ---\n"
            + logTail());
  }

  private String missingMainClassHint() {
    String out = logTail();
    return out.contains("Could not find or load main class") || out.contains("ClassNotFound")
        ? "\n(kv-server has no " + MAIN_CLASS + " yet; see docs/http-api.md, 'Starting a node')"
        : "";
  }

  private String logTail() {
    try {
      List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
      return String.join("\n", lines.subList(Math.max(0, lines.size() - 40), lines.size()));
    } catch (IOException e) {
      return "(could not read log: " + e + ")";
    }
  }

  private static String classpath() {
    String classpath = System.getProperty(CLASSPATH_PROPERTY);
    if (classpath == null || classpath.isBlank()) {
      throw new IllegalStateException(
          "System property '"
              + CLASSPATH_PROPERTY
              + "' is not set; run the acceptance tests through ./mvnw verify");
    }
    return classpath;
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      socket.setReuseAddress(true);
      return socket.getLocalPort();
    }
  }
}
