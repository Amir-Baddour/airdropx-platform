import { Badge } from "@/components/ui/badge";

const STATUS_STYLES: Record<string, string> = {
  DRAFT: "bg-status-draft/10 text-status-draft",
  VALIDATING: "bg-status-info/10 text-status-info",
  READY: "bg-status-info/10 text-status-info",
  SCHEDULED: "bg-status-info/10 text-status-info",
  QUEUED: "bg-status-pending/10 text-status-pending",
  RUNNING: "bg-status-pending/10 text-status-pending",
  COMPLETED: "bg-status-success/10 text-status-success",
  PARTIALLY_COMPLETED: "bg-status-partial/10 text-status-partial",
  FAILED: "bg-status-danger/10 text-status-danger",
  CANCELLED: "bg-status-draft/10 text-status-draft",
  PENDING: "bg-status-draft/10 text-status-draft",
  PROCESSING: "bg-status-pending/10 text-status-pending",
  SKIPPED: "bg-status-draft/10 text-status-draft",
};

function formatLabel(status: string): string {
  return status
    .toLowerCase()
    .split("_")
    .map((w) => w[0].toUpperCase() + w.slice(1))
    .join(" ");
}

export default function StatusBadge({ status }: { status: string }) {
  const style = STATUS_STYLES[status] ?? "bg-status-draft/10 text-status-draft";
  return <Badge className={style}>{formatLabel(status)}</Badge>;
}

