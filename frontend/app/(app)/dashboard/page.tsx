"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Plus } from "lucide-react";
import { getDashboard, DashboardSummary, ApiError } from "@/lib/api";
import StatusBadge from "@/components/StatusBadge";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";

export default function DashboardPage() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getDashboard()
      .then(setSummary)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load dashboard"));
  }, []);

  if (error) return <p className="text-destructive">{error}</p>;
  if (!summary) return <p className="text-muted-foreground">Loading…</p>;

  return (
    <div className="max-w-5xl">
      <div className="mb-8 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Dashboard</h1>
        <Button asChild>
          <Link href="/airdrops/new">
            <Plus size={16} />
            New airdrop
          </Link>
        </Button>
      </div>

      {/* Bento grid: 3 stat cards + a tall status card on the right, a wide recent-activity card below */}
      <div className="grid grid-cols-4 grid-rows-2 gap-4">
        <StatCard label="Total airdrops" value={summary.totalAirdrops} />
        <StatCard label="Recipients completed" value={summary.totalRecipientsCompleted} tone="success" />
        <StatCard label="Recipients failed" value={summary.totalRecipientsFailed} tone="danger" />

        <Card className="col-span-1 row-span-2">
          <CardHeader>
            <CardTitle>By status</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-2">
            {Object.keys(summary.byStatus).length === 0 && (
              <p className="text-sm text-muted-foreground">No airdrops yet.</p>
            )}
            {Object.entries(summary.byStatus).map(([status, count]) => (
              <div key={status} className="flex items-center justify-between">
                <StatusBadge status={status} />
                <span className="font-mono text-sm text-muted-foreground">{count}</span>
              </div>
            ))}
          </CardContent>
        </Card>

        <Card className="col-span-3 row-span-1">
          <CardHeader>
            <CardTitle>Recent airdrops</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            {summary.recentAirdrops.length === 0 ? (
              <div className="px-5 pb-5">
                <p className="text-sm text-muted-foreground">
                  No airdrops yet.{" "}
                  <Link href="/airdrops/new" className="font-medium text-primary">
                    Create your first one
                  </Link>
                </p>
              </div>
            ) : (
              <div>
                {summary.recentAirdrops.map((a, i) => (
                  <Link
                    key={a.id}
                    href={`/airdrops/${a.id}`}
                    className={`flex items-center justify-between px-5 py-3 hover:bg-muted/50 ${
                      i !== summary.recentAirdrops.length - 1 ? "border-b border-border" : ""
                    }`}
                  >
                    <span className="font-medium">{a.name}</span>
                    <StatusBadge status={a.status} />
                  </Link>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function StatCard({ label, value, tone }: { label: string; value: number; tone?: "success" | "danger" }) {
  const valueColor = tone === "success" ? "text-status-success" : tone === "danger" ? "text-status-danger" : "text-foreground";
  return (
    <Card className="col-span-1 row-span-1 flex flex-col justify-between">
      <CardHeader className="pb-0">
        <CardTitle>{label}</CardTitle>
      </CardHeader>
      <CardContent>
        <p className={`font-mono text-4xl font-semibold ${valueColor}`}>{value}</p>
      </CardContent>
    </Card>
  );
}
