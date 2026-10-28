package org.gianoziaorientale.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "agentsmith.rag", name = "enabled", havingValue = "true")
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping(path = "/api/rag/query", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public RagService.RagResponse query(@RequestBody QueryRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La richiesta RAG è obbligatoria.");
        }
        return ragService.query(request.question());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("La richiesta RAG non è valida."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> serviceFailure(Exception exception) {
        log.warn("RAG request failed ({})", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("Impossibile completare la richiesta RAG."));
    }

    public record QueryRequest(String question) {}

    public record ErrorResponse(String message) {}
}
