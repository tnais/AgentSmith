package org.gianoziaorientale.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AgentSmithOperatorClientTest {

    @Test
    void fetchesTheModuliContractAndSavesTheOpaqueModelIdWithOperatorAuthentication() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        AtomicReference<String> savedBody = new AtomicReference<>();
        server.createContext("/moduli", exchange -> {
            assertEquals("Basic b3BlcmF0b3I6c2VjcmV0", exchange.getRequestHeaders().getFirst("Authorization"));
            sendJson(exchange, """
                    [{"id":"openwebui:provider/model","nome":"Provider model"}]
                    """);
        });
        server.createContext("/api/rag/config", exchange -> {
            assertEquals("Basic b3BlcmF0b3I6c2VjcmV0", exchange.getRequestHeaders().getFirst("Authorization"));
            savedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, """
                    {"answerModelId":"openwebui:provider/model"}
                    """);
        });
        server.start();

        try {
            AgentSmithOperatorClient client = AgentSmithOperatorClient.create(
                    URI.create("http://localhost:" + server.getAddress().getPort()), "operator", "secret");

            assertEquals(
                    new ModuliAiService.Modulo("openwebui:provider/model", "Provider model"),
                    client.availableModels().getFirst());
            client.saveAnswerModel("openwebui:provider/model");
            assertTrue(savedBody.get().contains("\"answerModelId\":\"openwebui:provider/model\""));
        } finally {
            server.stop(0);
        }
    }

    private static void sendJson(com.sun.net.httpserver.HttpExchange exchange, String json)
            throws java.io.IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (var body = exchange.getResponseBody()) {
            body.write(response);
        }
    }
}
