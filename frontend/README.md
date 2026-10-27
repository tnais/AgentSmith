# ClientAI React frontend

The React application lives in its own `frontend` module, separate from the
Spring Boot backend.

To serve the frontend together with the API from the Spring Boot application,
configure the current Gemini key and, to list and use Open WebUI models, its
optional API base URL and API key:

```sh
export GEMINI_API_KEY=your-gemini-api-key
export OPEN_WEB_UI_BASE_URL=https://openwebui.mygor.xyz
export OPEN_WEB_UI_API_KEY=your-openwebui-api-key
mvn clean package
java -jar target/ClientAI.jar
```

Then open `http://localhost:8080`. Maven installs the pinned Node.js runtime,
builds the frontend, and packages it as Spring Boot static resources. The
`/moduli` endpoint lists Gemini and the models available through Open WebUI.
`POST /descrivi` accepts `linguaggio`, `code`, and the selected `modello`, and
returns the explanation plus the token count reported by the provider when
available.
The Open WebUI API key is optional when that server allows unauthenticated
model access.

For frontend development with Vite hot reload, start the backend with
`mvn spring-boot:run`, then run:

```sh
npm --prefix frontend ci
npm --prefix frontend run dev
```

Open the URL printed by Vite. Requests to `/descrivi` are proxied to
`http://localhost:8080` by default. To use a different backend during
development, set `API_PROXY_TARGET` before starting Vite.
