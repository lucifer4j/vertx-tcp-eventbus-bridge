package io.vertx.ext.eventbus.bridge.tcp.impl;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.BridgeOptions;
import io.vertx.ext.eventbus.bridge.tcp.BridgeEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class TCPJsonRPCStreamEventBusBridgeImpl extends JsonRPCStreamEventBusBridgeImpl<NetSocket> {

  public TCPJsonRPCStreamEventBusBridgeImpl(Vertx vertx, BridgeOptions options, Handler<BridgeEvent<NetSocket>> bridgeEventHandler) {
    super(vertx, options, bridgeEventHandler);
  }

  @Override
  public void handle(NetSocket socket) {
    checkCallHook(
      // process the new socket according to the event handler
      () -> new BridgeEventImpl<>(BridgeEventType.SOCKET_CREATED, null, socket),
      // on success
      () -> {
        final Map<String, MessageConsumer<?>> registry = new ConcurrentHashMap<>();
        final Map<String, Message<JsonObject>> replies = new ConcurrentHashMap<>();

        socket
          .exceptionHandler(t -> {
            log.error(t.getMessage(), t);
            registry.values().forEach(MessageConsumer::unregister);
            registry.clear();
          })
          .endHandler(v -> {
            registry.values().forEach(MessageConsumer::unregister);
            // normal close, trigger the event
            checkCallHook(() -> new BridgeEventImpl<>(BridgeEventType.SOCKET_CLOSED, null, socket));
            registry.clear();
          })
          .handler(
            // create a protocol parser
            new StreamParser()
              .exceptionHandler(t -> {
                log.error(t.getMessage(), t);
              })
              .handler(buffer -> {
                final Object msg = Json.decodeValue(buffer);
                if (!this.validate(msg)) {
                  // TODO: should we log? or send an error?
                  return;
                }
                // msg can now be either JsonObject or JsonArray or something else (e.g. String)
                // we should only care about JsonObjects or JsonArrays
                if (msg instanceof JsonObject) {
                  final JsonObject json = (JsonObject) msg;
                  dispatch(json, socket::write, registry, replies);
                  return;
                }
                if (msg instanceof JsonArray) {
                  final JsonArray json = (JsonArray) msg;
                  for (Object o : json) {
                    if (o instanceof JsonObject) {
                      // TODO: we need to try catch on ClassCastException to handle the case of bad batch like in the spec
                      //       and return an error
                      final JsonObject jsonObject = (JsonObject) o;
                      dispatch(jsonObject, socket::write, registry, replies);
                    }
                  }
                  return;
                }
                // TODO: this is a fully broken request it's not a JsonObject or JsonArray, we need to return an error
              }));
      },
      // on failure
      socket::close
    );
  }

  private void dispatch(JsonObject msg, Consumer<Buffer> socket, Map<String, MessageConsumer<?>> registry, Map<String, Message<JsonObject>> replies) {
    final String method = msg.getString("method");
    final Object id = msg.getValue("id");
    dispatch(
      socket,
      method,
      id,
      msg,
      registry,
      replies);
  }
}
