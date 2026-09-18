import { lazy, Suspense } from "react";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { Nav } from "./components/Nav";
import { ToastProvider } from "./toast";

const DashboardPage = lazy(() => import("./pages/DashboardPage").then((m) => ({ default: m.DashboardPage })));
const SourcesPage = lazy(() => import("./pages/SourcesPage").then((m) => ({ default: m.SourcesPage })));
const TargetsPage = lazy(() => import("./pages/TargetsPage").then((m) => ({ default: m.TargetsPage })));
const SubscriptionsPage = lazy(() =>
  import("./pages/SubscriptionsPage").then((m) => ({ default: m.SubscriptionsPage })),
);
const DeliveriesPage = lazy(() => import("./pages/DeliveriesPage").then((m) => ({ default: m.DeliveriesPage })));
const LivePage = lazy(() => import("./pages/LivePage").then((m) => ({ default: m.LivePage })));
const LoginPage = lazy(() => import("./pages/LoginPage").then((m) => ({ default: m.LoginPage })));

export function App() {
  return (
    <ToastProvider>
      <BrowserRouter>
        <Nav />
        <main className="content">
          <Suspense fallback={null}>
            <Routes>
              <Route path="/" element={<DashboardPage />} />
              <Route path="/sources" element={<SourcesPage />} />
              <Route path="/targets" element={<TargetsPage />} />
              <Route path="/subscriptions" element={<SubscriptionsPage />} />
              <Route path="/deliveries" element={<DeliveriesPage />} />
              <Route path="/live" element={<LivePage />} />
              <Route path="/login" element={<LoginPage />} />
            </Routes>
          </Suspense>
        </main>
      </BrowserRouter>
    </ToastProvider>
  );
}
