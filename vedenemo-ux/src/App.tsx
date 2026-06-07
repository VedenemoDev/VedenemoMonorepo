import { useEffect, useState } from "react";

type PingState = "idle" | "loading" | "ok" | "error";

type RuntimeConfig = {
  apiBaseUrl?: string;
};

async function loadRuntimeConfig(): Promise<RuntimeConfig> {
  const response = await fetch("/config.json", { cache: "no-store" });
  if (!response.ok) {
    return {};
  }
  return response.json() as Promise<RuntimeConfig>;
}

function normalizeBaseUrl(value: string): string {
  return value.replace(/\/+$/, "");
}

export function App() {
  const [apiBaseUrl, setApiBaseUrl] = useState("");
  const [pingState, setPingState] = useState<PingState>("idle");
  const [pingMessage, setPingMessage] = useState("Not checked");

  useEffect(() => {
    let cancelled = false;

    loadRuntimeConfig()
      .then((config) => {
        if (!cancelled) {
          setApiBaseUrl(normalizeBaseUrl(config.apiBaseUrl ?? ""));
        }
      })
      .catch(() => {
        if (!cancelled) {
          setPingState("error");
          setPingMessage("Backend config unavailable");
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  async function pingBackend() {
    if (!apiBaseUrl) {
      setPingState("error");
      setPingMessage("Backend URL is not configured");
      return;
    }

    setPingState("loading");
    setPingMessage("Pinging...");

    try {
      const response = await fetch(`${apiBaseUrl}/models/ping`, {
        headers: {
          Accept: "application/json",
        },
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const body = (await response.json()) as { status?: string };
      setPingState("ok");
      setPingMessage(body.status === "ok" ? "Backend responded OK" : "Backend responded");
    } catch (error) {
      setPingState("error");
      setPingMessage(error instanceof Error ? error.message : "Ping failed");
    }
  }

  return (
    <main className="app-shell">
      <section className="card">
        <h1>Vedenemo UX Deployment Check</h1>
        <p>The Firebase Hosting pipeline is now serving updates from GitHub Actions.</p>
        <p>This page is still intentionally minimal while the backend skeleton settles.</p>
        <div className="ping-panel">
          <button type="button" onClick={pingBackend} disabled={pingState === "loading"}>
            Ping
          </button>
          <span className={`ping-status ping-status-${pingState}`}>{pingMessage}</span>
        </div>
        <p className="backend-url">{apiBaseUrl || "Backend URL is not configured"}</p>
      </section>
    </main>
  );
}
