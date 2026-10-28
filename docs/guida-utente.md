# Guida d'uso e operativa

Questa guida descrive l'uso dell'interfaccia web, la configurazione dei modelli
e le funzioni opzionali di ricerca documentale (RAG).

## 1. Avviare l'applicazione

Servono JDK 25, Maven e una chiave API Gemini. Dalla radice del repository:

```sh
export GEMINI_API_KEY="la-tua-chiave-gemini"
mvn clean package
java -jar target/AgentSmith.jar
```

Maven installa Node.js e npm nella cartella `target`, compila il frontend e lo
inserisce nel JAR. L'applicazione è disponibile all'indirizzo
<http://localhost:8080>.

La chiave va tenuta in una variabile d'ambiente e non inserita nel codice o
nei documenti del progetto. In produzione, configura i segreti tramite il
gestore di segreti dell'ambiente di esecuzione.

### Sviluppo del frontend

Avvia il backend con `mvn spring-boot:run` e, in un secondo terminale:

```sh
npm --prefix frontend ci
npm --prefix frontend run dev
```

Apri l'indirizzo Vite mostrato nel terminale. Il proxy inoltra le richieste API
a `http://localhost:8080`; imposta `API_PROXY_TARGET` prima dell'avvio di Vite
per puntare a un backend diverso.

## 2. Spiegare codice dall'interfaccia

1. Nel campo **Linguaggio**, specifica il linguaggio del frammento, per esempio
   `Python`, `Java` o `C`.
2. Incolla o scrivi il codice nell'editor.
3. Seleziona un **Modulo IA**. L'elenco include i modelli Gemini utilizzabili
   e, se configurato, quelli disponibili sul server Open WebUI.
4. Seleziona **Spiega il codice**. L'output appare nel pannello a destra.
5. Se necessario, copia la spiegazione o scaricala come `ai-answer.txt`.
   **Pulisci** azzera codice e risultato.

Il conteggio dei token è mostrato soltanto quando il provider lo restituisce.
Il modello predefinito è `gemini-3.8-flash`. Le risposte sono generate da un
servizio IA: verifica il codice e le spiegazioni prima di usarli.

### Configurare Open WebUI

Le variabili seguenti sono facoltative; senza URL si usa il valore predefinito:

```sh
export OPEN_WEB_UI_BASE_URL="https://openwebui.example.org"
export OPEN_WEB_UI_API_KEY="la-tua-chiave-open-webui"
```

La chiave è facoltativa solo se il server consente l'accesso senza
autenticazione. La chiave Gemini resta necessaria per l'avvio. Riavvia il
backend dopo aver modificato la configurazione.

## 3. API dell'applicazione

L'interfaccia utilizza i seguenti endpoint:

| Metodo e percorso | Descrizione |
|---|---|
| `GET /moduli` | Elenca i modelli disponibili. |
| `POST /descrivi` | Genera una spiegazione del codice. |
| `GET /actuator/health` | Stato di salute dell'applicazione. |

Esempio di richiesta di spiegazione:

```sh
curl -X POST http://localhost:8080/descrivi \
  -H 'Content-Type: application/json' \
  -d '{"linguaggio":"Python","code":"print(1 + 1)","modello":"gemini:gemini-3.8-flash"}'
```

Il corpo richiede `linguaggio`, `code` e `modello`. Per un modello Open WebUI,
il valore di `modello` è `openwebui:<id-del-modello>`; per Gemini è
`gemini:<nome-del-modello>`. La risposta contiene `spiegazione` e `token`
(quest'ultimo può essere `null`). Gli errori restituiscono un messaggio
descrittivo nel campo `messaggio`.

## 4. Ricerca documentale RAG (facoltativa)

La funzione RAG recupera passaggi pertinenti da un indice Elasticsearch e
chiede al modello di rispondere usando il materiale trovato. È disabilitata
per impostazione predefinita e non è necessaria per spiegare codice.

### Configurazione

Imposta le variabili seguenti prima di avviare il backend con RAG:

| Variabile | Obbligatoria | Descrizione |
|---|---:|---|
| `AGENTSMITH_RAG_ENABLED` | Sì | Impostare a `true` per abilitare la funzione. |
| `GEMINI_API_KEY` | Sì | Chiave usata anche per generare embedding. |
| `EMBEDDING_MODEL` | Sì | Nome del modello Gemini per gli embedding, identico a quello usato durante l'indicizzazione. |
| `EMBEDDING_DIMENSIONS` | Sì | Dimensione vettoriale positiva, uguale a quella dell'indice. |
| `ELASTICSEARCH_URL` | Sì | URL HTTPS del cluster Elasticsearch, senza credenziali incorporate. |
| `AGENTSMITH_ES_READ_API_KEY` | Sì | API key Elasticsearch con permesso di lettura sull'alias RAG. |
| `RAG_ANSWER_MODEL_ID` | Sì | ID iniziale del modello per le risposte, per esempio `gemini:gemini-3.8-flash`. |
| `RAG_INDEX_ALIAS` | No | Alias dell'indice. Predefinito: `agentsmith-rag-v1-active`. Deve iniziare con `agentsmith-rag-v1-`. |
| `AGENTSMITH_RAG_CONFIG_FILE` | No | File locale per la selezione del modello di risposta; predefinito `./data/agentsmith-rag.properties`. |

Esempio di avvio:

```sh
export AGENTSMITH_RAG_ENABLED=true
export GEMINI_API_KEY="la-tua-chiave-gemini"
export EMBEDDING_MODEL="text-embedding-004"
export EMBEDDING_DIMENSIONS="768"
export ELASTICSEARCH_URL="https://elasticsearch.example.org"
export AGENTSMITH_ES_READ_API_KEY="chiave-di-sola-lettura"
export RAG_ANSWER_MODEL_ID="gemini:gemini-3.8-flash"
java -jar target/AgentSmith.jar
```

Usa il nome del modello e la dimensione effettivamente supportati dal provider
e configurati nell'indice. I valori di esempio non sostituiscono la verifica
della mappatura Elasticsearch. L'alias deve essere stato predisposto con la
mappatura vettoriale attesa dall'applicazione.

Per interrogare il corpus:

```sh
curl -X POST http://localhost:8080/api/rag/query \
  -H 'Content-Type: application/json' \
  -d '{"question":"Come si configura il servizio?"}'
```

La risposta contiene `answer` e `sources`, con percorso e righe dei passaggi
recuperati. Se non sono trovati passaggi pertinenti, l'applicazione lo indica
senza inventare una risposta basata su fonti assenti. La domanda non può essere
vuota e, per impostazione predefinita, non può superare 4.000 caratteri.

### Configurare l'operatore RAG

La selezione del modello RAG tramite API è disabilitata per impostazione
predefinita. Per abilitarla, configura un utente operatore e un hash BCrypt:

```sh
export AGENTSMITH_RAG_SECURITY_ENABLED=true
export AGENTSMITH_RAG_OPERATOR_USERNAME="operatore"
export AGENTSMITH_RAG_OPERATOR_PASSWORD_HASH='{bcrypt}$2a$...'
```

Il valore dell'hash deve essere completo e valido, comprensivo del prefisso
`{bcrypt}`. Non usare la password in chiaro come valore di questa variabile.
Quando la protezione è attiva, `POST /api/rag/config` e
`/actuator/metrics` richiedono HTTP Basic con il ruolo operatore; il resto
dell'applicazione non richiede autenticazione. Usa HTTPS fuori da un ambiente
locale fidato e non esporre pubblicamente l'applicazione senza un ulteriore
livello di autenticazione e controllo degli accessi.

## 5. Importare documenti nel corpus

La CLI importa una cartella locale in Elasticsearch, usando Gemini per
calcolare gli embedding. Il backend deve essere avviato e il suo endpoint di
selezione del modello deve essere abilitato come descritto sopra.

Configura l'ambiente della CLI:

```sh
export AGENTSMITH_BASE_URL="http://localhost:8080"
export AGENTSMITH_RAG_OPERATOR_USERNAME="operatore"
export AGENTSMITH_RAG_OPERATOR_PASSWORD="password-operatore"
export GEMINI_API_KEY="la-tua-chiave-gemini"
export EMBEDDING_MODEL="text-embedding-004"
export EMBEDDING_DIMENSIONS="768"
export ELASTICSEARCH_URL="https://elasticsearch.example.org"
export AGENTSMITH_ES_WRITE_API_KEY="chiave-di-scrittura"
export RAG_INDEX_ALIAS="agentsmith-rag-v1-active"
java -jar target/AgentSmith-cli.jar
```

La CLI chiede il percorso della cartella; per completarlo si può digitare una
parte del percorso e premere **Tab**, quindi **Invio**. Controlla il percorso
risolto e digita `YES` per confermarlo. Scegli poi il modello di risposta dal
catalogo (usa `next` e `prev` per cambiare pagina, se necessario). Solo dopo
aver salvato il modello la CLI avvia l'indicizzazione.

La CLI accetta HTTP solo per `localhost`, `127.0.0.1` o `::1`; gli endpoint
remoti devono usare HTTPS. Elasticsearch deve avere già l'alias e la
mappatura compatibili. Usa chiavi distinte e con privilegi minimi per lettura
e scrittura; la chiave di scrittura è solo per la CLI.

Sono indicizzati file regolari leggibili con contenuto testuale UTF-8. File
vuoti, binari, collegamenti simbolici e file oltre 5 MiB vengono saltati.
Una singola esecuzione è limitata a 10.000 file e 20.000 voci complessive.
I testi superiori a 5.000 byte sono divisi in blocchi per l'indicizzazione.
Se si verificano errori sui file, la CLI evita la pulizia globale dei file
obsoleti, così da non rimuovere documenti che non è riuscita a rileggere.

## 6. Diagnostica rapida

- **L'applicazione non parte e segnala `GEMINI_API_KEY` mancante**: imposta la
  variabile nell'ambiente del processo e riavvia.
- **Elenco modelli non disponibile**: controlla la connessione e le credenziali
  Gemini/Open WebUI. L'interfaccia può comunque mostrare il modello Gemini
  predefinito quando il catalogo non è raggiungibile.
- **Errore di quota o troppe richieste**: verifica la quota del provider e
  riprova dopo qualche istante.
- **RAG non pronto**: verifica che RAG sia abilitato e che URL, chiavi,
  modello di embedding, dimensione e alias siano coerenti con Elasticsearch.
  `GET /actuator/health` fornisce lo stato senza esporre dettagli sensibili.
- **La CLI non riesce a salvare la selezione del modello**: verifica che
  l'endpoint del backend sia raggiungibile, che le credenziali corrispondano e
  che `AGENTSMITH_RAG_SECURITY_ENABLED=true` sia configurato nel backend.
- **Indicizzazione incompleta**: leggi il riepilogo prodotto dalla CLI e
  correggi i file indicati prima di ripetere l'importazione.
