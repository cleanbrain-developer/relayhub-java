import { NavLink, useNavigate } from "react-router-dom";
import { clearCredentials, isLoggedIn } from "../auth";

export function Nav() {
  const navigate = useNavigate();
  const loggedIn = isLoggedIn();

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
      <span className="nav-spacer" />
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
