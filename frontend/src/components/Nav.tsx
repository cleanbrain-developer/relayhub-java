import { useEffect, useState } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import { clearCredentials, isLoggedIn } from "../auth";
import { useTheme } from "../theme";
import { fetchAllTimeCount, fetchTodayCount, recordVisit } from "../visitorCounter";

export function Nav() {
  const navigate = useNavigate();
  const loggedIn = isLoggedIn();
  const [theme, toggleTheme] = useTheme();
  const [todayCount, setTodayCount] = useState<number | null>(null);
  const [allTimeCount, setAllTimeCount] = useState<number | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      await recordVisit();
      const [today, allTime] = await Promise.all([fetchTodayCount(), fetchAllTimeCount()]);
      if (!cancelled) {
        setTodayCount(today);
        setAllTimeCount(allTime);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  function logout() {
    clearCredentials();
    navigate("/login");
    setMenuOpen(false);
  }

  return (
    <nav className="nav">
      <div className="nav-bar">
        <span className="brand">RelayHub Console</span>
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
        <button
          className="nav-toggle"
          onClick={() => setMenuOpen(!menuOpen)}
          aria-label={menuOpen ? "Close menu" : "Open menu"}
          aria-expanded={menuOpen}
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            {menuOpen ? (
              <path d="M6 6l12 12M18 6l-12 12" />
            ) : (
              <path d="M4 7h16M4 12h16M4 17h16" />
            )}
          </svg>
        </button>
      </div>
      <div className={`nav-links${menuOpen ? " nav-links-open" : ""}`} onClick={() => setMenuOpen(false)}>
        <NavLink to="/">Dashboard</NavLink>
        <NavLink to="/sources">Sources</NavLink>
        <NavLink to="/targets">Targets</NavLink>
        <NavLink to="/subscriptions">Subscriptions</NavLink>
        <NavLink to="/deliveries">Deliveries</NavLink>
        <NavLink to="/live">Live</NavLink>
        {(todayCount !== null || allTimeCount !== null) && (
          <span className="nav-visitor-count" aria-label="Visitor count">
            {todayCount !== null && <>Today · {todayCount}</>}
            {todayCount !== null && allTimeCount !== null && " · "}
            {allTimeCount !== null && <>All · {allTimeCount}</>}
          </span>
        )}
        <span className="nav-spacer" />
        {loggedIn ? (
          <button className="link-button" onClick={logout}>
            Log out
          </button>
        ) : (
          <NavLink to="/login">Log in</NavLink>
        )}
      </div>
    </nav>
  );
}
