import { FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import { saveCredentials } from "../auth";

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
    try {
      const res = await fetch("/api/auth/check", {
        headers: { Authorization: "Basic " + btoa(`${username}:${password}`) },
      });
      if (!res.ok) {
        setError(res.status === 401 ? "Incorrect username or password." : `Login check failed: HTTP ${res.status}`);
        return;
      }
      saveCredentials({ username, password });
      navigate("/");
    } catch (err) {
      setError("Login check failed: " + (err as Error).message);
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
