package org.gianoziaorientale.agent;

import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import dev.langchain4j.exception.*;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private final AgenteSmith agenteSmith;
    private final ModuliAiService moduliAiService;

    public App(AgenteSmith agenteSmith, ModuliAiService moduliAiService) {
        this.agenteSmith = agenteSmith;
        this.moduliAiService = moduliAiService;
    }

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @PostMapping("/descrivi")
    public ResponseEntity<ModuliAiService.Risposta> descrivi(@RequestBody DescriviRequest request) {
        ModuliAiService.Risposta risposta = moduliAiService.spiega(
                agenteSmith,
                UUID.randomUUID().toString(),
                request.linguaggio(),
                request.code(),
                request.modello());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(risposta);
    }

    @GetMapping("/moduli")
    public java.util.List<ModuliAiService.Modulo> moduli() {
        return moduliAiService.moduliDisponibili();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroreResponse> gestisciErroreModello(Exception e) {
        log.error("Errore durante la comunicazione con il modello", e);
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            switch (t) {
                case RateLimitException _ -> {
                    return errore(HttpStatus.TOO_MANY_REQUESTS,
                            "Il modello ha ricevuto troppe richieste o la quota è esaurita. Riprova tra qualche istante.");
                }
                case IllegalArgumentException _ -> {
                    return errore(HttpStatus.BAD_REQUEST, "Il modulo AI selezionato non è valido.");
                }
                case AuthenticationException _ -> {
                    return errore(HttpStatus.BAD_GATEWAY,
                            "Autenticazione al modello non riuscita. Contatta l'amministratore.");
                }
                case ModelNotFoundException _ -> {
                    return errore(HttpStatus.BAD_GATEWAY, "Il modello configurato non è disponibile.");
                }
                case ContentFilteredException _ -> {
                    return errore(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Il contenuto è stato bloccato dai filtri del modello.");
                }
                case InvalidRequestException _ -> {
                    return errore(HttpStatus.BAD_GATEWAY, "Il modello ha rifiutato la richiesta (richiesta non valida).");
                }
                case TimeoutException _, HttpTimeoutException _, SocketTimeoutException _ -> {
                    return errore(HttpStatus.GATEWAY_TIMEOUT,
                            "Il modello non ha risposto in tempo. Riprova più tardi.");
                }
                case HttpException h -> {
                    return errore(HttpStatus.BAD_GATEWAY, descriviHttp(h.statusCode()));
                }
                case InternalServerException _ -> {
                    return errore(HttpStatus.BAD_GATEWAY,
                            "Il servizio del modello ha restituito un errore interno. Riprova più tardi.");
                }
                case ConnectException _, UnknownHostException _ -> {
                    return errore(HttpStatus.SERVICE_UNAVAILABLE,
                            "Impossibile raggiungere il modello. Riprova più tardi.");
                }
                default -> { }
            }
        }
        return errore(HttpStatus.BAD_GATEWAY, "Errore durante la comunicazione con il modello.");
    }

    private static String descriviHttp(int status) {
        return switch (status) {
            case 400 -> "Il modello ha rifiutato la richiesta (HTTP 400).";
            case 401, 403 -> "Accesso al modello negato (HTTP " + status + "). Contatta l'amministratore.";
            case 404 -> "Modello o endpoint non trovato (HTTP 404).";
            case 429 -> "Troppe richieste al modello (HTTP 429). Riprova tra qualche istante.";
            case 503 -> "Il modello è temporaneamente sovraccarico (HTTP 503). Riprova più tardi.";
            default -> status >= 500
                    ? "Il servizio del modello ha un problema (HTTP " + status + "). Riprova più tardi."
                    : "Il modello ha risposto con un errore (HTTP " + status + ").";
        };
    }

    private static ResponseEntity<ErroreResponse> errore(HttpStatus status, String messaggio) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(new ErroreResponse(messaggio));
    }

    public record ErroreResponse(String messaggio) {}

    public record DescriviRequest(String linguaggio, String code, String modello) {}
}
