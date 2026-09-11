import { BrowserRouter, Route, Routes } from "react-router-dom";
import { Nav } from "./components/Nav";
import { DashboardPage } from "./pages/DashboardPage";
import { SourcesPage } from "./pages/SourcesPage";
import { TargetsPage } from "./pages/TargetsPage";
import { SubscriptionsPage } from "./pages/SubscriptionsPage";
import { DeliveriesPage } from "./pages/DeliveriesPage";
import { LivePage } from "./pages/LivePage";
import { LoginPage } from "./pages/LoginPage";

export function App() {
  return (
    <BrowserRouter>
      <Nav />
      <main className="content">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/sources" element={<SourcesPage />} />
          <Route path="/targets" element={<TargetsPage />} />
          <Route path="/subscriptions" element={<SubscriptionsPage />} />
          <Route path="/deliveries" element={<DeliveriesPage />} />
          <Route path="/live" element={<LivePage />} />
          <Route path="/login" element={<LoginPage />} />
        </Routes>
      </main>
    </BrowserRouter>
  );
}
