# JSON-RPC as wire protocol for the event bus bridge

## Summary

To make the event bus bridge more accessible to users, we propose to add JSON-RPC as a message format for using
the event bus. In addition, the new wire protocol is supported on WebSockets and HTTP (with some caveats) transports.

## Introduction

Eclipse Vert.x offers a message-driven programming model based on an event bus that allows applications to
scale to multiple processes or nodes without requiring code changes or knowledge during development. The event
bus can be extended to non-native Vert.x applications, including other platforms such as nodejs, python, etc.
Currently, the event bus only supports a vert.x specific custom wire format for messages. To make the bridge more
accessible to users and allow them to use existing JSON-RPC libraries to communicate with the event bus, we propose
to add JSON-RPC as another message format for using the event bus.

[JSON-RPC](https://www.jsonrpc.org/specification) is a stateless, light-weight remote procedure call (RPC) protocol.

## Message Format Differences

For instance, this is the format of a send message.

<table>
    <tr>
        <td>Vert.X Custom Format</td>
        <td>JSON RPC Message format</td>
    </tr>
    <tr>
        <td>

        ```json
        {
            "type": "send",
            "address": "eventbusAddress",
            "body": {
                "number": 7
            },
            "replyAddress": "<unique_id>"
        }
        ```

        </td>
        <td>

        ```json
        {
            "jsonrpc": "2.0",
            "method": "send",
            "id": "<unique_id>",
            "params": {
                "address": "eventbusAddress",
                "body": {
                    "number": 7
                }
            }
        }

        ```

        </td>
    </tr>
</table>

This is how a response from the respective bridges looks like:

<table>
    <tr>
        <td>

        ```json
        {
            "type": "message",
            "address": "eventbusAddress",
            "replyAddress": "<unique_id>",
            "headers": {},
            "body": {
                "type": "prime"
            },
            "send": true
        }
        ```

        </td>
        <td>

        ```json
        {
            "jsonrpc": "2.0",
            "id": "uuid",
            "result": {
                "replyAddress": "<unique_id>",
                "body": {
                    "type": "prime"
                },
                "headers": {}
            }
        }
        ```

        </td>
    </tr>
</table>

## Usage

On the server side, working with the bridges' does not change much. For using the legacy bridge, one would do:

```java
TcpEventBusBridge bridge = TcpEventBusBridge.create(
    vertx,
    new BridgeOptions()
        .addInboundPermitted(new PermittedOptions().setAddress("in"))
        .addOutboundPermitted(new PermittedOptions().setAddress("out")));

bridge.listen(7000, res -> {
  if (res.succeeded()) {
    // succeed...
  } else {
    // fail...
  }
});
```

When using the new bridge, users can obtain the appropriate bridge handler and connect it to their server. For instance,
```java
JsonRPCBridgeOptions options = new JsonRPCBridgeOptions()
  .addInboundPermitted(new PermittedOptions().setAddress("in"))
  .addOutboundPermitted(new PermittedOptions().setAddress("out"))
  // if set to false, then websockets messages are received on
  // frontend as binary frames
  .setWebsocketsTextAsFrame(true);

Handler<WebSocketBase> bridge = JsonRPCStreamEventBusBridge
  .webSocketHandler(vertx, options, null);

vertx
  .createHttpServer()
  .requestHandler(req -> {
    // this is where any http request will land
    if ("/jsonrpc".equals(req.path())) {
      // we switch from HTTP to WebSocket
      req.toWebSocket().onSuccess(bridge::handle);
    } else {
      // serve the base HTML application ...
    }
  })
  .listen(8080);
```

The new JSON RPC bridge also comes with new goodies like support for Websockets and HTTP transports
(including SSE).

For some more demos, visit [jsonrpc bridge demos](https://github.com/lucifer4j/vertx-tcp-eventbus-bridge/tree/experimental/jsonrpc/src/main/java/examples).
