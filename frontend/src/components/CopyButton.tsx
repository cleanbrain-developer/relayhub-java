import { useState } from "react";

/**
 * Small "copy to clipboard" affordance for the URLs an operator most often needs to hand to a real
 * external system — a Source Event's ingress URL, a Target Endpoint's full URL (Stage 3, maintainer
 * request 2026-09-30: "URL-copy affordances"). Falls back to a manual-select hint if the Clipboard
 * API is unavailable (non-HTTPS context, older browser) instead of failing silently.
 */
export function CopyButton({ value }: { value: string }) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      setCopied(false);
      alert(`Copy not available in this browser — select and copy manually:\n\n${value}`);
    }
  }

  return (
    <button type="button" className="copy-button" onClick={copy} title="Copy to clipboard">
      {copied ? "Copied!" : "Copy"}
    </button>
  );
}
