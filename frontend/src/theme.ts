import { useEffect, useState } from "react";

export type Theme = "light" | "dark";

const STORAGE_KEY = "relayhub-console-theme";

function readStored(): Theme | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw === "light" || raw === "dark" ? raw : null;
  } catch {
    // Private-browsing / storage-blocked — fall through to the default.
    return null;
  }
}

/** Dark wins whenever there's no explicit saved choice — deliberately not `prefers-color-scheme`,
 *  which would default a light-OS visitor to light. See index.html's inline script: it applies
 *  this same default synchronously, before first paint, so there's no light-theme flash. */
export function getTheme(): Theme {
  return readStored() ?? "dark";
}

export function applyTheme(theme: Theme): void {
  document.documentElement.setAttribute("data-theme", theme);
  try {
    localStorage.setItem(STORAGE_KEY, theme);
  } catch {
    // Non-fatal — the page still renders in the chosen theme this session, just doesn't persist.
  }
}

export function useTheme(): [Theme, () => void] {
  const [theme, setTheme] = useState<Theme>(getTheme);

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  function toggle() {
    setTheme((t) => (t === "dark" ? "light" : "dark"));
  }

  return [theme, toggle];
}
