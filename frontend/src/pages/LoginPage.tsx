import { FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import { saveCredentials } from "../auth";
import { ApiError, post } from "../api";

/**
 * No dedicated /api/auth/check endpoint exists — this verifies credentials by actually creating
 * and immediately deleting a throwaway Source, the cheapest existing protected write. A wrong
 * password surfaces as a 401 from that call rather than silently "succeeding" at login.
 */
export function LoginPage() {
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);
  const navigate = useNavigate();

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setChecking(true);
    saveCredentials({ username, password });
    try {
      const probeKey = `__login-check-${Date.now()}`;
      await post(`/api/sources`, { key: probeKey, name: "login check", description: "login check" });
      await fetch(`/api/sources/${probeKey}`, {
        method: "DELETE",
        headers: { Authorization: "Basic " + btoa(`${username}:${password}`) },
      });
      navigate("/");
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setError("Incorrect username or password.");
      } else {
        setError("Login check failed: " + (err as Error).message);
      }
    } finally {
      setChecking(false);
    }
  }

  return (
    <div className="card" style={{ maxWidth: 360, margin: "4rem auto" }}>
      <h1>Log in</h1>
      <form onSubmit={handleSubmit}>
        <label>
          Username
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoFocus />
        </label>
        <label>
          Password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={checking}>
          {checking ? "Checking..." : "Log in"}
        </button>
      </form>
    </div>
  );
}
