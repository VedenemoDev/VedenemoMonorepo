export type ModelChangeEvent = {
  type: "connected" | "model-changed";
  modelAzName: string | null;
  occurredAt: string;
};

type ModelChangeEventHandlers = {
  onOpen: () => void;
  onClose: () => void;
  onError: (message: string) => void;
  onModelChanged: (modelAzName: string) => void;
};

export class ModelChangeEventAdapter {
  private socket: WebSocket | null = null;

  connect(apiBaseUrl: string, handlers: ModelChangeEventHandlers) {
    this.disconnect();

    const socket = new WebSocket(modelEventsUrl(apiBaseUrl));
    this.socket = socket;

    socket.addEventListener("open", handlers.onOpen);
    socket.addEventListener("close", handlers.onClose);
    socket.addEventListener("error", () => handlers.onError("Model event connection failed"));
    socket.addEventListener("message", (event) => {
      const message = parseMessage(event.data);
      if (message?.type === "model-changed" && message.modelAzName) {
        handlers.onModelChanged(message.modelAzName);
      }
    });
  }

  disconnect() {
    if (this.socket !== null) {
      this.socket.close();
      this.socket = null;
    }
  }
}

function modelEventsUrl(apiBaseUrl: string): string {
  const url = new URL(apiBaseUrl);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  url.pathname = `${url.pathname.replace(/\/+$/, "")}/models/events`;
  url.search = "";
  url.hash = "";
  return url.toString();
}

function parseMessage(data: unknown): ModelChangeEvent | null {
  if (typeof data !== "string") {
    return null;
  }

  try {
    return JSON.parse(data) as ModelChangeEvent;
  } catch {
    return null;
  }
}
