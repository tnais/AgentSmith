package org.gianoziaorientale.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ModuliAiServiceTest {

    @Test
    void readsAllAvailableModelsFromGeminiAndOpenWebUi() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/models", exchange -> {
            byte[] response = """
                    [{"id":"example-model","name":"Example Model"}]
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.createContext("/v1beta/models", exchange -> {
            assertEquals("test-gemini-key", exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            String responseBody;
            if (exchange.getRequestURI().getRawQuery().contains("pageToken=next-page")) {
                responseBody = """
                        {"models":[
                          {"name":"models/gemini-second","displayName":"Gemini Second",
                           "supportedGenerationMethods":["generateContent"]}
                        ]}
                        """;
            } else {
                responseBody = """
                        {"models":[
                          {"name":"models/gemini-first","displayName":"Gemini First",
                           "supportedGenerationMethods":["generateContent"]},
                          {"name":"models/custom-image","displayName":"Nano Banana",
                           "supportedGenerationMethods":["generateContent"]},
                          {"name":"models/gemini-audio","displayName":"Gemini Audio",
                           "supportedGenerationMethods":["generateContent"]},
                          {"name":"models/imagen","displayName":"Imagen",
                           "supportedGenerationMethods":["generateContent"]},
                          {"name":"models/embedding-only","displayName":"Embedding",
                           "supportedGenerationMethods":["embedContent"]}
                        ],"nextPageToken":"next-page"}
                        """;
            }
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();

        try {
            ModuliAiService service = new ModuliAiService(
                    "http://localhost:" + server.getAddress().getPort(),
                    "",
                    "test-gemini-key",
                    "http://localhost:" + server.getAddress().getPort());

            assertEquals(
                    java.util.List.of(
                            new ModuliAiService.Modulo("gemini:gemini-first", "Gemini First"),
                            new ModuliAiService.Modulo("gemini:gemini-second", "Gemini Second"),
                            new ModuliAiService.Modulo("openwebui:example-model", "Example Model")),
                    service.moduliDisponibili());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void includesTokenCountReportedByOpenWebUi() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/models", exchange -> {
            byte[] response = """
                    [{"id":"example-model","name":"Example Model"}]
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.createContext("/api/chat/completions", exchange -> {
            byte[] response = """
                    {"choices":[{"message":{"content":"La spiegazione"}}],
                     "usage":{"prompt_tokens":12,"completion_tokens":8,"total_tokens":20}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.createContext("/v1beta/models", exchange -> {
            byte[] response = """
                    {"models":[]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();

        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            ModuliAiService service = new ModuliAiService(baseUrl, "", "test-gemini-key", baseUrl);

            assertEquals(
                    new ModuliAiService.Risposta("La spiegazione", 20),
                    service.spiega(null, "test-session", "Java", "class Main {}", "openwebui:example-model"));
        } finally {
            server.stop(0);
        }
    }
}
