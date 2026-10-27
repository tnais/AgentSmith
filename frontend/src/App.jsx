import { useEffect, useState } from "react";

const initialCode = `#include <stdio.h>

int main() {
    printf("Hello, World!");
    return 0;
}`;
const defaultModule = { id: "gemini:gemini-3.8-flash", nome: "Gemini 3.8 Flash" };

export default function App() {
  const [language, setLanguage] = useState("C");
  const [moduleId, setModuleId] = useState(defaultModule.id);
  const [modules, setModules] = useState([defaultModule]);
  const [modulesError, setModulesError] = useState("");
  const [code, setCode] = useState(initialCode);
  const [answer, setAnswer] = useState("");
  const [tokenCount, setTokenCount] = useState(null);
  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");
  const [copied, setCopied] = useState(false);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let active = true;
    fetch("/moduli")
      .then(async (response) => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json();
      })
      .then((availableModules) => {
        if (!active) return;
        setModules(availableModules);
        if (!availableModules.some((module) => module.id === moduleId)) {
          setModuleId(
            availableModules.find((module) => module.id.startsWith("gemini:"))?.id ??
              availableModules[0]?.id ??
              defaultModule.id,
          );
        }
      })
      .catch(() => {
        if (active) {
          setModulesError(
            "Elenco modelli non disponibile; Gemini predefinito resta selezionabile.",
          );
        }
      });
    return () => {
      active = false;
    };
  }, []);

  async function handleSubmit(event) {
    event.preventDefault();
    setAnswer("");
    setTokenCount(null);
    setError("");
    setActionError("");
    setCopied(false);
    setLoading(true);

    try {
      const response = await fetch("/descrivi", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ linguaggio: language, code, modello: moduleId }),
      });
      if (!response.ok) {
        let message = `Richiesta non riuscita (HTTP ${response.status}).`;
        try {
          const body = await response.json();
          if (body && body.messaggio) message = body.messaggio;
        } catch {
          // corpo non JSON: si mantiene il messaggio generico
        }
        throw new Error(message);
      }
      const body = await response.json();
      setAnswer(body.spiegazione);
      setTokenCount(body.token);
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "Non è stato possibile contattare il servizio.",
      );
    } finally {
      setLoading(false);
    }
  }

  function handleClear() {
    setCode("");
    setAnswer("");
    setTokenCount(null);
    setError("");
    setActionError("");
    setCopied(false);
  }

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(answer);
      setCopied(true);
      setActionError("");
    } catch {
      setActionError("Impossibile copiare la spiegazione negli appunti.");
      setCopied(false);
    }
  }

  function handleDownload() {
    try {
      const file = new Blob([answer], { type: "text/plain;charset=utf-8" });
      const url = URL.createObjectURL(file);
      const link = document.createElement("a");
      link.href = url;
      link.download = "ai-answer.txt";
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      setActionError("");
    } catch {
      setActionError("Impossibile scaricare la spiegazione.");
    }
  }

  return (
    <main className="page-shell">
      <header className="topbar">
        <a className="brand" href="/" aria-label="Agente Smith, pagina iniziale">
          <span className="brand-mark" aria-hidden="true">
            S
          </span>
          <span>AGENTE SMITH</span>
        </a>
        <span className="topbar-note">ANALISI DEL CODICE</span>
      </header>

      <section className="intro" aria-labelledby="page-title">
        <p className="eyebrow">
          <span className="status-dot" />
          IL TUO COMPAGNO DI PROGRAMMAZIONE
        </p>
        <div className="headline">
          <h1 id="page-title">
            Il codice parla.
            <br />
            <span>Smith lo spiega.</span>
          </h1>
          <img className="smith-photo" src="/Smith.jpeg" alt="Smith" />
        </div>
        <p className="intro-copy">
          Scegli un modulo IA e un linguaggio, incolla uno snippet e ottieni
          una spiegazione chiara del suo funzionamento.
        </p>
      </section>

      <form className="workspace" onSubmit={handleSubmit}>
        <section className="editor-panel" aria-labelledby="editor-title">
          <div className="panel-heading">
            <div>
              <p className="panel-kicker">01 / INPUT</p>
              <h2 id="editor-title">Il tuo codice</h2>
            </div>
            <div className="editor-controls">
              <label className="module-control">
                <span>Modulo IA</span>
                <select
                  aria-label="Modulo di intelligenza artificiale"
                  value={moduleId}
                  onChange={(event) => setModuleId(event.target.value)}
                >
                  {modules.map((module) => (
                    <option key={module.id} value={module.id}>
                      {module.nome}
                    </option>
                  ))}
                </select>
              </label>
              <label className="language-control">
                <span>Linguaggio</span>
                <input
                  aria-label="Linguaggio di programmazione"
                  required
                  value={language}
                  onChange={(event) => setLanguage(event.target.value)}
                  placeholder="Es. Python"
                  maxLength={40}
                />
              </label>
            </div>
          </div>
          {modulesError && (
            <p className="module-error" role="status">
              {modulesError}
            </p>
          )}
          <label className="sr-only" htmlFor="code">
            Codice da analizzare
          </label>
          <textarea
            id="code"
            className="code-input"
            required
            spellCheck="false"
            value={code}
            onChange={(event) => setCode(event.target.value)}
            placeholder="Incolla qui il tuo codice..."
          />
          <div className="editor-footer">
            <span>
              {code.split("\n").length} RIGHE
              <span className="footer-separator">/</span>
              {code.length} CARATTERI
            </span>
            <div className="footer-actions">
              <button
                className="clear-button"
                type="button"
                onClick={handleClear}
                disabled={loading || (!code && !answer && !error)}
              >
                Pulisci
              </button>
              <button className="submit-button" type="submit" disabled={loading}>
                {loading ? (
                  <>
                    <span className="spinner" aria-hidden="true" />
                    Sto analizzando
                  </>
                ) : (
                  <>
                    Spiega il codice <span aria-hidden="true">↗</span>
                  </>
                )}
              </button>
            </div>
          </div>
        </section>

        <section className="answer-panel" aria-labelledby="answer-title">
          <div className="panel-heading">
            <div>
              <p className="panel-kicker">02 / OUTPUT</p>
              <h2 id="answer-title">La spiegazione</h2>
            </div>
            {answer && (
              <div className="answer-actions">
                <button
                  className="answer-action-button"
                  type="button"
                  onClick={handleCopy}
                >
                  {copied ? "Copied!" : "Copy to Clipboard"}
                </button>
                <button
                  className="answer-action-button"
                  type="button"
                  onClick={handleDownload}
                >
                  Download
                </button>
              </div>
            )}
          </div>
          <div
            className={`answer-content${answer ? " has-answer" : ""}`}
            aria-live="polite"
            aria-busy={loading}
          >
            {error ? (
              <p className="error-message" role="alert">
                {error}
              </p>
            ) : answer ? (
              <>
                <p className="answer-text">{answer}</p>
                {actionError && (
                  <p className="answer-action-error" role="alert">
                    {actionError}
                  </p>
                )}
              </>
            ) : (
              <div className="empty-state">
                <span className="empty-icon" aria-hidden="true">
                  {"{ }"}
                </span>
                <p>La spiegazione apparirà qui.</p>
                <span>Pronto quando lo sei tu.</span>
              </div>
            )}
          </div>
          <div className="answer-footer">
            <span>
              <span className="status-dot" />
              {loading ? "SMITH STA LEGGENDO" : "IN ATTESA DEL TUO CODICE"}
            </span>
            {answer && (
              <span>
                {tokenCount == null
                  ? "COSTO TOKEN NON DISPONIBILE"
                  : `COSTO: ${tokenCount} TOKEN`}
              </span>
            )}
            <span>AGENTE SMITH · AI</span>
          </div>
        </section>
      </form>

      <footer className="page-footer">
        <span>UN CODICE ALLA VOLTA. NESSUN MISTERO.</span>
        <span>© AGENTE SMITH</span>
      </footer>
    </main>
  );
}
