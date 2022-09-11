---
layout: home
title: GSoC-2022 Project Report
---

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

For instance, this is the format of a send message the legacy Vert.X format.

~~~~~ json
{
    "jsonrpc": "2.0",
    "method": "send",
    "id": "unique_id",
    "params": {
        "address": "eventbusAddress",
        "body": {
            "number": 7
        }
    }
}
~~~~~

On the other hand, the JSON-RPC message format looks like:

~~~~~ json
{
  "type": "send",
  "address": "eventbusAddress",
  "body": {
      "number": 7
  },
  "replyAddress": "unique_id"
}
~~~~~

This is how a response from the Vert.x Bridge looks like:

~~~~~ json
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
~~~~~

And the response from the JSON RPC Bridge looks like:

~~~~~ json
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
~~~~~

## Usage

On the server side, working with the bridges' does not change much. For using the legacy bridge, one would do:

~~~~~ java
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
~~~~~

When using the new bridge, users can obtain the appropriate bridge handler and connect it to their server. For instance,
~~~~~ java
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
~~~~~

The new JSON RPC bridge also comes with new goodies like support for Websockets and HTTP transports (including SSE).

## Work Product

Over the course of the project, the following improvements have been added to the bridge.

* Add Websockets and HTTP transports in addition to the existing TCP transport for the JSON RPC bridge.
* The Websockets transport can be configured to send messages in text or binary.
* A basic JSON schema is included in the resources and loaded from classpath at runtime to validate the messages received on the bridge.
* Add separate tests for each transport.
* Message Type Support
  * Websockets and TCP support all message types.
  * The HTTP transport does not support publish/send from server to client and unregister method.
  * HTTP transport supports SSE.
* HTTP transport has a convenience method to access SSE because SSE doesn't allow POST or request body.
* Demos added for all transports.
* Lots of refactorings around writing the message, see commits for more details.

## Demos

* [WebsocketBridgeExample](https://github.com/lucifer4j/vertx-tcp-eventbus-bridge/blob/experimental/jsonrpc/src/main/java/examples/WebsocketBridgeExample.java)
demonstrates the use of JSON RPC bridge using Websockets transport and browsers' WebSockets API. Websockets transport is at feature parity with the
TCP one. It allows for bidirectional communication on the bridge and supports both subscribing and request/response models.
* [HttpBridgeExample](https://github.com/lucifer4j/vertx-tcp-eventbus-bridge/blob/experimental/jsonrpc/src/main/java/examples/HttpBridgeExample.java)
demonstrates the use of JSON RPC bridge using HTTP transport. The HTTP transport does not support triggering communication from the server side.
Therefore, sending/publishing from the bridge directly is not possible. Some methods like unregister are not supported on the HTTP transport because
those don't make sense (ending a request means unregistering automatically and the client cannot send data after a request has been made to
unregister manually). The transport supports request/response model well.
* [HttpSSEBridgeExample](https://github.com/lucifer4j/vertx-tcp-eventbus-bridge/blob/experimental/jsonrpc/src/main/java/examples/HttpSSEBridgeExample.java)
shows how to use the JSON RPC bridge with browser SSE APIs to register to the event bus addresses. Once a client has registered using SSE,
it can normally receive send/publish events from those addresses on the bridge.
