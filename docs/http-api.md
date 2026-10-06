# dkv HTTP API

The client-facing contract of a dkv node. The acceptance tests are written against this file, so an implementation that follows it passes regardless of how it's built inside.

Sections are added as stages need them. A later stage may add headers, status codes, or routes, but never changes the meaning of anything already here.

## Starting a node (Stage 1)

The `kv-server` module provides a main class:

```
dev.jason.dkv.server.Main --port <port>
```

- The node listens for HTTP/1.1 on `<port>`. The tests connect to `127.0.0.1:<port>`, so bind to all interfaces or to loopback.
- The node is ready once the port accepts connections. The tests wait up to 15 seconds for that.
- The node runs until it's terminated (the tests send SIGTERM). Nothing is persisted in Stage 1.
- Whatever the node prints to stdout or stderr is saved by the tests to `acceptance/target/node-logs/`, which is the first place to look when a test fails.
- Later stages add more arguments (for example a data directory). Stage 1 passes only `--port`.

## Keys and values

- Keys and values are UTF-8 strings.
- A key is exactly one path segment after `/kv/`, percent-encoded (RFC 3986). The server percent-decodes it as UTF-8. `+` is a literal plus sign, not a space. A `/` inside a key arrives as `%2F` and is part of the key.
- A key must be non-empty. A value may be empty; an empty value is different from a missing key.
- A request body is the raw UTF-8 value, with no JSON or other wrapping.
- A `PUT` with no body or a zero-length body stores the empty string. `GET` and `DELETE` ignore any request body.

## Routes (Stage 1)

| Request | Outcome | Status | Response body |
|---|---|---|---|
| `GET /kv/{key}` | key present | `200 OK` | the value, `Content-Type: text/plain; charset=utf-8` |
| | key absent | `404 Not Found` | anything |
| `PUT /kv/{key}` with body = value | key was absent, now created | `201 Created` | empty |
| | key was present, value replaced | `204 No Content` | empty |
| `DELETE /kv/{key}` | key was present, now removed | `204 No Content` | empty |
| | key was absent | `404 Not Found` | anything |
| `GET`, `PUT` or `DELETE` on `/kv/` (empty key) | invalid key | `400 Bad Request` | anything |
| any other method on `/kv/...` | not supported | `405 Method Not Allowed` | anything |
| any path outside `/kv/` | unknown route | `404 Not Found` | anything |

Every status code above is part of the result of an operation, not decoration. For example, when two clients create the same key at the same time, exactly one of them sees `201`.

## Reserved for later stages

Don't give these a meaning of your own; later stages define them.

- Request headers whose names start with `Dkv-`.
- `409 Conflict`, `307 Temporary Redirect` with a `Location` header, and `503 Service Unavailable`.
