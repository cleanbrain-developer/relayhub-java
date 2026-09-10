export function StatusBadge({ value }: { value: string }) {
  const tone: Record<string, string> = {
    ACTIVE: "badge badge-ok",
    SUCCEEDED: "badge badge-ok",
    SUCCESS: "badge badge-ok",
    INACTIVE: "badge badge-muted",
    PENDING: "badge badge-warn",
    DEAD: "badge badge-danger",
    FAILED: "badge badge-danger",
  };
  return <span className={tone[value] ?? "badge"}>{value}</span>;
}
