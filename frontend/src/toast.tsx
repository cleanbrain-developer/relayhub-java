import { createContext, useCallback, useContext, useRef, useState, ReactNode } from "react";

type ToastKind = "success" | "error";

interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
}

interface ToastContextValue {
  notify: (message: string, kind?: ToastKind) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

const AUTO_DISMISS_MS = { success: 3500, error: 6000 };

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(0);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((t) => t.id !== id));
  }, []);

  const notify = useCallback(
    (message: string, kind: ToastKind = "success") => {
      const id = nextId.current++;
      setToasts((current) => [...current, { id, kind, message }]);
      setTimeout(() => dismiss(id), AUTO_DISMISS_MS[kind]);
    },
    [dismiss],
  );

  return (
    <ToastContext.Provider value={{ notify }}>
      {children}
      <div className="toast-stack" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast-${t.kind}`}>
            <span>{t.message}</span>
            <button className="toast-close" aria-label="Dismiss" onClick={() => dismiss(t.id)}>
              &times;
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

/** Falls back to a no-op outside <ToastProvider> (e.g. in component tests) rather than throwing,
 *  since most pages/components don't need a real provider wired up just to render. */
export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  return ctx ?? { notify: () => {} };
}
