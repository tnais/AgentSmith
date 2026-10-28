package org.gianoziaorientale.ingestor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ElasticsearchDocumentIndexTest {

    @Test
    void bulkIndexesEscapedJsonAgainstOnlyTheValidatedAliasAndDeletesStaleChunks() throws Exception {
        List<Request> requests = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(new Request(
                    exchange.getRequestURI().toString(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] response = (exchange.getRequestURI().getPath().endsWith("_bulk")
                    ? "{\"errors\":false,\"items\":[]}"
                    : "{\"failures\":[],\"deleted\":1}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();
        try {
            String alias = "agentsmith-rag-v1-active";
            RestClient client = RestClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .build();
            ElasticsearchDocumentIndex index = new ElasticsearchDocumentIndex(client, alias);
            index.index(List.of(new DocumentIndex.IndexedDocumentChunk(
                    "stable-id", "corpus-id", "file-id", "file-id", "src/a.txt",
                    "content-hash", 0, "line one\n\"quoted\"", 1, 2, new float[] {0.25f, -0.5f})));
            index.deleteChunksAtOrAbove("file-id", 1);
            index.deleteFilesNotInCorpus("corpus-id", java.util.Set.of("file-id"));

            assertEquals(3, requests.size());
            assertEquals("/_bulk?refresh=wait_for", requests.getFirst().target());
            assertTrue(requests.getFirst().contentType().startsWith("application/x-ndjson"));
            assertTrue(requests.getFirst().body().contains("\"_index\":\"" + alias + "\""));
            assertTrue(requests.getFirst().body().contains("\"text\":\"line one\\n\\\"quoted\\\"\""));
            assertTrue(requests.get(1).target().startsWith("/" + alias + "/_delete_by_query"));
            assertTrue(requests.get(1).body().contains("\"sourceId\":\"file-id\""));
            assertTrue(requests.get(1).body().contains("\"gte\":1"));
            assertTrue(requests.get(2).body().contains("\"corpusId\":\"corpus-id\""));
            assertTrue(requests.get(2).body().contains("\"fileId\":[\"file-id\"]"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsAnUnscopedAliasBeforeAnyWriteCanBeMade() {
        assertThrows(IllegalArgumentException.class, () -> new ElasticsearchDocumentIndex(
                RestClient.create(), "other-application-index"));
    }

    private record Request(String target, String contentType, String body) {}
}
