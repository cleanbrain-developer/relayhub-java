import { ReactNode, useState } from "react";

export interface TabDef {
  id: string;
  label: string;
  content: ReactNode;
}

/**
 * Minimal tab strip for the List -> Detail pattern (Stage 3 of the integration-platform overhaul,
 * maintainer request 2026-09-30) — Sources/Targets/Subscriptions detail pages each render one of
 * these instead of the old always-expanded inline sections. Uncontrolled (owns its own active-tab
 * state) since nothing outside a detail page needs to read or drive which tab is showing.
 */
export function Tabs({ tabs, initialId }: { tabs: TabDef[]; initialId?: string }) {
  const [activeId, setActiveId] = useState(initialId ?? tabs[0]?.id);
  const active = tabs.find((t) => t.id === activeId) ?? tabs[0];

  return (
    <div className="tabs">
      <div className="tab-strip" role="tablist">
        {tabs.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={t.id === active?.id}
            className={t.id === active?.id ? "tab tab-active" : "tab"}
            onClick={() => setActiveId(t.id)}
          >
            {t.label}
          </button>
        ))}
      </div>
      <div className="tab-panel">{active?.content}</div>
    </div>
  );
}
