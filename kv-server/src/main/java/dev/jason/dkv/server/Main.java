package dev.jason.dkv.server;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/*
HTTP method	          -> getRequestMethod()
Path / query string	  -> getRequestURI().getPath(), .getQuery()
Request headers	      -> getRequestHeaders().getFirst("Authorization")
Request body	        -> getRequestBody() (an InputStream)
Response headers	    -> getResponseHeaders().set(...) (before sending headers)
Status + length	      -> sendResponseHeaders(status, length)
Response body	        -> getResponseBody() (an OutputStream)
 */

public class Main {

  public static void main(String[] args) throws IOException {

    Map<String, String> opts = new HashMap<>();
    for (int i = 0; i < args.length; i++) {
      if (args[i].startsWith("--") && i + 1 < args.length) {
        opts.put(args[i].substring(2), args[++i]);
      }
    }
    System.out.println(opts.toString());
    String port = opts.getOrDefault("port", "8080");

    HttpServer server = HttpServer.create(new InetSocketAddress(Integer.valueOf(port)), 0);

    server.createContext("/", new UnknownPathHandler());
    server.createContext("/kv", new KeyValueHandler());

    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    System.out.println("Server is listening on port " + port + "...");
    server.start();
  }
}
