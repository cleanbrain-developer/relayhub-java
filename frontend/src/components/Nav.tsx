import { useEffect, useState } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import { clearCredentials, isLoggedIn } from "../auth";
import { useTheme } from "../theme";
import { fetchTodayCount, recordVisitOnce } from "../visitorCounter";

export function Nav() {
  const navigate = useNavigate();
  const loggedIn = isLoggedIn();
  const [theme, toggleTheme] = useTheme();
  const [todayCount, setTodayCount] = useState<number | null>(null);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      await recordVisitOnce();
      const count = await fetchTodayCount();
      if (!cancelled) setTodayCount(count);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  function logout() {
    clearCredentials();
    navigate("/login");
  }

  return (
    <nav className="nav">
      <span className="brand">RelayHub Console</span>
      <NavLink to="/">Dashboard</NavLink>
      <NavLink to="/sources">Sources</NavLink>
      <NavLink to="/targets">Targets</NavLink>
      <NavLink to="/subscriptions">Subscriptions</NavLink>
      <NavLink to="/deliveries">Deliveries</NavLink>
      <NavLink to="/live">Live</NavLink>
      {todayCount !== null && (
        <span className="nav-visitor-count" aria-label="Today's visitor count">
          Today · {todayCount}
        </span>
      )}
      <span className="nav-spacer" />
      <button
        className="theme-toggle"
        onClick={toggleTheme}
        title={theme === "dark" ? "Switch to light theme" : "Switch to dark theme"}
        aria-label="Toggle color theme"
      >
        {theme === "dark" ? (
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" />
          </svg>
        ) : (
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="12" cy="12" r="4" />
            <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
          </svg>
        )}
      </button>
      {loggedIn ? (
        <button className="link-button" onClick={logout}>
          Log out
        </button>
      ) : (
        <NavLink to="/login">Log in</NavLink>
      )}
    </nav>
  );
}
