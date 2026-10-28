# Agent Smith RAG Implementation Plan

Run these prompts in order. Each prompt is scoped to one implementation stage; review and test its changes before moving to the next. Do not invent infrastructure details that are still unknown: document the missing decision and keep the affected feature safely disabled until it is supplied.

## Step 1 — Verify compatibility and define contracts

```text
Read ArchitecturePlan.md and inspect the existing Maven/Spring Boot/LangChain4j application. Implement only the compatibility and contract groundwork for the RAG feature. Verify the available LangChain4j 1.22.0 Elasticsearch-store and embedding-provider integrations against the project's dependency setup, and identify any facts that must be supplied externally (Elasticsearch server/version and vector capabilities, embedding model/dimensions, similarity metric). Define typed configuration and documented contracts for the /moduli model catalog, operator model selection, supported document types, and the 5,000-byte file threshold. Do not guess missing server/model values or add credentials. Preserve existing behavior and endpoints. Add focused tests for any contracts/configuration introduced, and report unresolved external prerequisites.
```

## Step 2 — Establish the Elasticsearch data boundary

```text
Implement the Agent Smith Elasticsearch isolation described in ArchitecturePlan.md, following existing repository conventions. Add configurable, validated Agent Smith-only index/alias naming with the required agentsmith-rag-v1- prefix; reject arbitrary or unscoped names. Configure TLS with certificate verification and separate runtime-read and CLI-write credentials, supplied only through runtime configuration. Add deployment/configuration artifacts for the dedicated index template, mapping, alias, and least-privilege roles only if the repository has an established place and the required server/vector details are known; otherwise document the exact external administrator prerequisites without fabricating them. Ensure missing required settings fail clearly and never fall back to an unscoped index. Add tests for name validation and configuration failure cases.
```

## Step 3 — Secure operator-only configuration changes

```text
Inspect the existing Spring Security and API setup, then implement the RAG access policy from ArchitecturePlan.md: RAG reads/queries are public, while state-changing model-configuration operations require the dedicated operator role. Keep Elasticsearch credentials and ingestion operations out of public APIs. Preserve /descrivi and /moduli behavior, and avoid broad security changes to unrelated routes. Add tests proving anonymous reads work, anonymous/unauthorized configuration writes are rejected, and an authorized operator can update configuration.
```

## Step 4 — Implement retrieval and answer generation

```text
Implement Agent Smith's RAG query path using the project's LangChain4j 1.22.0-compatible integrations and the configured embedding provider. Embed the question, search only the validated Agent Smith index/alias using the runtime's read-only identity, and compose an answer with the configured allowed chat model. Treat retrieved documents as untrusted reference material, not instructions; enforce sensible input/result limits and return only permitted source metadata. Add a public, read-only query endpoint without changing /descrivi or /moduli. Add focused tests for retrieval scoping, prompt/context composition, source attribution, failure handling, anonymous query access, and existing endpoint behavior. Do not enable the feature if required external model or Elasticsearch configuration is absent.
```

## Step 5 — Create the separate ingestion CLI

```text
Add a separate Java CLI entry point and executable packaging for document-ingestion setup; it must not make the web application an ingester or an always-running service. Use standard Java input/output classes only, with no third-party terminal library. Fetch the available chat-model catalog from Agent Smith's /moduli endpoint, render letter/model choices in multiple columns that fit within 80 terminal columns (wrap, never truncate long names), validate the selection, and save it through an operator-authorized Agent Smith configuration operation. Support directory completion by accepting a partial path followed by Tab and Enter, expanding unique directory matches, and listing ambiguous matches for retry; require resolved-path confirmation before work starts. Keep answer-model selection separate from embedding configuration. Leave file processing, indexing, and per-file progress to Step 6. Add focused CLI tests and document how to launch the CLI.
```

## Step 6 — Add safe document processing and indexing

**Status: implemented.**

```text
Implement recursive ingestion of regular files without filtering by filename extension. Attempt each file as bounded UTF-8 text, including extensionless files and unfamiliar suffixes; report unreadable, binary, malformed UTF-8, and oversized files without treating them as successfully ingested. Never execute files or invoke executable/format-specific parsers. Treat each readable text file as a standalone document: files of 5,000 bytes or fewer produce one snippet; larger files are split with overlap at suitable technical boundaries. Preserve validated source path/file identity, chunk location, and version metadata; generate vectors with the separately configured embedding model and write only to the validated Agent Smith index/alias using stable IDs/upserts. Define and implement re-ingestion and deletion so stale chunks are removed safely. Enforce file/size limits. Add tests for traversal, extension-independent text handling, threshold boundaries, chunking/overlap, metadata, stable IDs, stale-chunk cleanup, and errors.
```

## Step 7 — Operationalize and document

**Status: implemented.**

```text
Complete the operational safeguards for the RAG service and CLI. Add appropriate readiness/liveness reporting, redacted structured logs, timeouts, size limits, and metrics for ingestion, retrieval, model latency, and failures, following existing project patterns. Never log credentials, retrieved document contents, or secrets. Document required runtime settings, the separate least-privilege identities, index/alias migration, re-ingestion, rollback, and local CLI usage in the repository's existing documentation locations. Add focused tests for health/configuration behavior and verify logs do not include sensitive values.
```

## Step 8 — Align container packaging and deployment

**Status: implemented.**

```text
Align the existing container packaging with ArchitecturePlan.md. Keep Elasticsearch external; do not add an Elasticsearch node to the application Compose deployment. Fix the Docker build's stale ClientAI.jar reference to use the actual Maven artifact AgentSmith.jar, and verify Docker/ZIP packaging does not depend on the stale filename. Keep the web app on port 8080, run it non-root with a read-only filesystem where practical, and pass secrets only through runtime environment/secret injection—not build arguments, frontend variables, labels, or committed Compose files. Package the CLI as a separate executable artifact or one-shot image without baking credentials or documents into it. Add/update deployment documentation and validate the relevant Maven packaging/build steps.
```

## Step 9 — Final end-to-end verification

**Status: implemented; external Elasticsearch role checks remain pending.**

```text
Review the completed RAG implementation against every requirement in ArchitecturePlan.md. Run the focused Java, frontend, packaging, and security-boundary tests available in the repository, then run the appropriate full build. Verify the existing /descrivi and /moduli endpoints remain intact; public query reads work and configuration writes require the operator role; Agent Smith can read only its own corpus and cannot write; the CLI can write only to its own corpus; and negative cross-application access checks reject access. Report executed checks and results, remaining external prerequisites, and any unverified infrastructure-dependent behavior. Do not claim role-boundary verification unless it was exercised with isolated test identities or provisioned identities.
```
