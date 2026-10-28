# Agente Smith

Agente Smith spiega frammenti di codice tramite Gemini o modelli esposti da
Open WebUI. Include inoltre una funzione RAG opzionale per interrogare un
corpus di documenti indicizzato in Elasticsearch.

## Avvio rapido

Requisiti: JDK 25 e Maven. Per creare il pacchetto completo non serve installare
Node.js manualmente: Maven scarica la versione prevista e compila il frontend.

```sh
export GEMINI_API_KEY="la-tua-chiave-gemini"
mvn clean package
java -jar target/AgentSmith.jar
```

Apri <http://localhost:8080>. Incolla il codice, indica il linguaggio, scegli
un modulo IA e seleziona **Spiega il codice**. La risposta può essere copiata
negli appunti o scaricata. La chiave Gemini è necessaria anche quando si usa
Open WebUI, perché Gemini è il modello predefinito dell'applicazione.

Per eseguire il frontend separatamente durante lo sviluppo:

```sh
mvn spring-boot:run
npm --prefix frontend ci
npm --prefix frontend run dev
```

Usa l'indirizzo stampato da Vite. Le richieste API vengono inoltrate al backend
su `http://localhost:8080`; per un altro backend imposta `API_PROXY_TARGET`
prima di avviare Vite.

## Documentazione

- [Guida d'uso e operativa](docs/guida-utente.md): configurazione, interfaccia,
  API, RAG, importazione documenti e risoluzione dei problemi.

## Pacchetti

- `target/AgentSmith.jar`: applicazione web con frontend incluso.
- `target/AgentSmith-cli.jar`: CLI interattiva per selezionare il modello RAG
  e indicizzare una cartella di documenti.
