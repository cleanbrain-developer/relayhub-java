import { AuthenticationType } from "../types";

// Only NONE/API_KEY are functionally wired server-side today — the rest are declared but inert
// (see AuthenticationType.java). Offering them here would look functional without being so, which
// the maintainer's spec explicitly said not to do.
const SELECTABLE_TYPES: AuthenticationType[] = ["NONE", "API_KEY"];

export interface AuthFormState {
  authenticationType: AuthenticationType;
  authenticationConfig: string;
}

/**
 * Shared Authentication form fields for Source/Target's General tab (Stage 3, maintainer request
 * 2026-09-30). {@code authenticationConfig} is a secret — the API never echoes it back (see
 * SourceResponse/TargetResponse), so this field is always write-only: blank means "leave the
 * existing value untouched" (SourceService.update/TargetService.update both honor that), never
 * "clear it".
 */
export function AuthenticationFields({
  state,
  onChange,
  hasExistingSecret,
}: {
  state: AuthFormState;
  onChange: (next: AuthFormState) => void;
  /** Whether this Source/Target already has authenticationType != NONE — purely informational, so
   *  the operator knows blank really does mean "unchanged" and not "none configured". */
  hasExistingSecret: boolean;
}) {
  return (
    <div className="form-grid">
      <label>
        Authentication type
        <select
          value={state.authenticationType}
          onChange={(e) => onChange({ ...state, authenticationType: e.target.value as AuthenticationType })}
        >
          {SELECTABLE_TYPES.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>
      </label>
      <label>
        {state.authenticationType === "NONE" ? "Secret value" : "Secret value (API key)"}
        <input
          type="password"
          autoComplete="off"
          placeholder={hasExistingSecret ? "•••••••• (leave blank to keep the existing value)" : "Not set"}
          value={state.authenticationConfig}
          onChange={(e) => onChange({ ...state, authenticationConfig: e.target.value })}
          disabled={state.authenticationType === "NONE"}
        />
      </label>
      {hasExistingSecret && (
        <p className="muted form-wide" style={{ margin: 0 }}>
          A secret is already configured and is never displayed once set. Leave this field blank to keep it; type a
          new value only to replace it.
        </p>
      )}
    </div>
  );
}
