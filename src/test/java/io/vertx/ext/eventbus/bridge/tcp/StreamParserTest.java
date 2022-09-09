package io.vertx.ext.eventbus.bridge.tcp;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.eventbus.bridge.tcp.impl.StreamParser;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class StreamParserTest {

  @Rule
  public RunTestOnContext rule = new RunTestOnContext();

  @Test(timeout = 30_000)
  public void testParseSimple(TestContext should) {
    final Async test = should.async();
    final StreamParser parser = new StreamParser()
      .exceptionHandler(should::fail)
      .handler(body -> {
        // extra line feed and carriage return are ignored
        should.assertEquals("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"hi\"}", body.toString());
        test.complete();
      });

    parser.handle(Buffer.buffer(
      "\r\n" +
      "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"hi\"}"));
  }

  @Test(timeout = 30_000)
  public void testParseSimpleWithPreambleFail(TestContext should) {
    final Async test = should.async();
    final StreamParser parser = new StreamParser()
      .exceptionHandler(err -> test.complete())
      .handler(body -> should.fail("There is something else than JSON in the preamble of the buffer"));

    parser.handle(Buffer.buffer(
      "Content-Length: 38\r\n" +
        "Content-Type: application/vscode-jsonrpc;charset=utf-8\r\n" +
        "\r\n" +
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"hi\"}"));
  }

  @Test(timeout = 30_000)
  public void testParseSimpleHeaderless(TestContext should) {
    final Async test = should.async();
    final StreamParser parser = new StreamParser()
      .exceptionHandler(should::fail)
      .handler(body -> {
        System.out.println(body.toString());
        test.complete();
      });

    parser.handle(Buffer.buffer("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"hi\"}\r\n"));
  }

  @Test(timeout = 30_000)
  public void testParseSimpleHeaderlessBatch(TestContext should) {
    final Async test = should.async();
    final StreamParser parser = new StreamParser()
      .exceptionHandler(should::fail)
      .handler(body -> {
        Object json = Json.decodeValue(body);
        // it's a batch!
        should.assertTrue(json instanceof JsonArray);
        test.complete();
      });

    parser.handle(Buffer.buffer("[\n" +
      "        {\"jsonrpc\": \"2.0\", \"method\": \"sum\", \"params\": [1,2,4], \"id\": \"1\"},\n" +
      "        {\"jsonrpc\": \"2.0\", \"method\": \"notify_hello\", \"params\": [7]},\n" +
      "        {\"jsonrpc\": \"2.0\", \"method\": \"subtract\", \"params\": [42,23], \"id\": \"2\"},\n" +
      "        {\"foo\": \"boo\"},\n" +
      "        {\"jsonrpc\": \"2.0\", \"method\": \"foo.get\", \"params\": {\"name\": \"myself\"}, \"id\": \"5\"},\n" +
      "        {\"jsonrpc\": \"2.0\", \"method\": \"get_data\", \"id\": \"9\"} \n" +
      "    ]\r\n"));
  }

  @Test(timeout = 30_000)
  public void testParseSimpleHeaderlessBatchFromSpec(TestContext should) {
    final Async test = should.async();
    final StreamParser parser = new StreamParser()
      .exceptionHandler(should::fail)
      .handler(body -> {
        Object json = Json.decodeValue(body);
        // it's a batch!
        should.assertTrue(json instanceof JsonArray);
        test.complete();
      });

    parser.handle(Buffer.buffer("[]\r\n"));
  }

  @Test(timeout = 30_000)
  public void testParseSimpleHeaderlessBatchFromSpec2(TestContext should) {
    final Async test = should.async();
    final StreamParser parser = new StreamParser()
      .exceptionHandler(should::fail)
      .handler(body -> {
        Object json = Json.decodeValue(body);
        // it's a batch!
        should.assertTrue(json instanceof JsonArray);
        test.complete();
      });

    parser.handle(Buffer.buffer("[1, 2, 3]\r\n"));
  }
}
