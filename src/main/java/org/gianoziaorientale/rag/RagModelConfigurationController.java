package org.gianoziaorientale.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RagModelConfigurationController {

    private static final Logger log = LoggerFactory.getLogger(RagModelConfigurationController.class);

    private final RagModelConfigurationService configurationService;

    public RagModelConfigurationController(RagModelConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @PostMapping("/api/rag/config")
    public ModelSelectionResponse selectAnswerModel(@RequestBody ModelSelectionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("RAG model selection body is required.");
        }
        configurationService.selectAnswerModel(request.answerModelId());
        return new ModelSelectionResponse(request.answerModelId());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> configurationFailure(Exception exception) {
        log.warn("RAG model configuration failed ({})", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("Unable to save RAG model configuration."));
    }

    public record ModelSelectionRequest(String answerModelId) {}

    public record ModelSelectionResponse(String answerModelId) {}

    public record ErrorResponse(String message) {}
}
