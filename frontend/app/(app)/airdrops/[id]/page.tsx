"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import {
  getAirdrop, listRecipients, listEvents, addRecipients, validateAirdrop, launchAirdrop, cancelAirdrop,
  AirdropResponse, RecipientResponse, AirdropEventResponse, ApiError,
} from "@/lib/api";
import StatusBadge from "@/components/StatusBadge";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from "@/components/ui/table";
import { Progress } from "@/components/ui/progress";

const LIVE_STATUSES = new Set(["QUEUED", "RUNNING"]);
const CANCELLABLE_STATUSES = new Set(["DRAFT", "VALIDATING", "READY", "QUEUED"]);

export default function AirdropDetailPage() {
  const { id } = useParams<{ id: string }>();

  const [airdrop, setAirdrop] = useState<AirdropResponse | null>(null);
  const [recipients, setRecipients] = useState<RecipientResponse[]>([]);
  const [events, setEvents] = useState<AirdropEventResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [recipientsInput, setRecipientsInput] = useState("");
  const [recipientsError, setRecipientsError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    const [a, r, e] = await Promise.all([getAirdrop(id), listRecipients(id), listEvents(id)]);
    setAirdrop(a);
    setRecipients(r.items);
    setEvents(e.items);
  }, [id]);

  useEffect(() => {
    // Initial data fetch on mount — the setState calls happen after awaited network I/O, not synchronously.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    refresh().catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load airdrop"));
  }, [refresh]);

  useEffect(() => {
    if (!airdrop || !LIVE_STATUSES.has(airdrop.status)) return;
    const interval = setInterval(() => refresh().catch(() => {}), 2000);
    return () => clearInterval(interval);
  }, [airdrop, refresh]);

  async function handleAddRecipients() {
    setRecipientsError(null);
    const lines = recipientsInput.split("\n").map((l) => l.trim()).filter(Boolean);
    if (lines.length === 0) {
      setRecipientsError("Add at least one line in the form: address,amount");
      return;
    }
    const parsed: { recipientAddress: string; amount: number }[] = [];
    for (const line of lines) {
      const [address, amountStr] = line.split(",").map((p) => p.trim());
      const amount = Number(amountStr);
      if (!address || !amountStr || Number.isNaN(amount) || amount <= 0) {
        setRecipientsError(`Couldn't parse line: "${line}" — expected "address,amount"`);
        return;
      }
      parsed.push({ recipientAddress: address, amount });
    }
    setActionLoading(true);
    try {
      await addRecipients(id, parsed);
      setRecipientsInput("");
      await refresh();
    } catch (err) {
      setRecipientsError(err instanceof ApiError ? err.message : "Failed to add recipients");
    } finally {
      setActionLoading(false);
    }
  }

  async function runAction(action: () => Promise<unknown>) {
    setError(null);
    setActionLoading(true);
    try {
      await action();
      await refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Action failed");
    } finally {
      setActionLoading(false);
    }
  }

  if (error && !airdrop) return <p className="text-destructive">{error}</p>;
  if (!airdrop) return <p className="text-muted-foreground">Loading…</p>;

  const done = recipients.filter((r) => r.status === "COMPLETED" || r.status === "FAILED").length;
  const failed = recipients.filter((r) => r.status === "FAILED").length;
  const pct = recipients.length === 0 ? 0 : Math.round((done / recipients.length) * 100);

  return (
    <div className="max-w-3xl">
      <Link href="/airdrops" className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-primary">
        <ArrowLeft size={14} /> All airdrops
      </Link>

      <div className="mb-2 flex items-center gap-3">
        <h1 className="text-2xl font-semibold">{airdrop.name}</h1>
        <StatusBadge status={airdrop.status} />
      </div>
      {airdrop.description && <p className="mb-6 text-muted-foreground">{airdrop.description}</p>}

      <div className="mb-8 grid grid-cols-3 gap-4">
        <MiniStat label="Asset" value={airdrop.assetType} />
        <MiniStat label="Recipients" value={String(airdrop.recipientCount)} />
        <MiniStat label="Total amount" value={airdrop.totalAmount.toLocaleString()} />
      </div>

      {error && <p className="mb-4 text-sm text-destructive">{error}</p>}

      {airdrop.status === "DRAFT" && (
        <Card className="mb-8">
          <CardHeader>
            <CardTitle className="text-foreground">Add recipients</CardTitle>
            <p className="text-sm text-muted-foreground">One per line: <code className="font-mono">address,amount</code></p>
          </CardHeader>
          <CardContent>
            <Textarea
              value={recipientsInput}
              onChange={(e) => setRecipientsInput(e.target.value)}
              rows={5}
              placeholder={"0xabc123...,100.5\n0xdef456...,250"}
              className="font-mono text-sm"
            />
            {recipientsError && <p className="mt-2 text-sm text-destructive">{recipientsError}</p>}
            <div className="mt-3 flex gap-2">
              <Button variant="outline" onClick={handleAddRecipients} disabled={actionLoading}>
                Add recipients
              </Button>
              {airdrop.recipientCount > 0 && (
                <Button onClick={() => runAction(() => validateAirdrop(id))} disabled={actionLoading}>
                  Validate
                </Button>
              )}
            </div>
          </CardContent>
        </Card>
      )}

      {airdrop.status === "READY" && (
        <Card className="mb-8">
          <CardHeader>
            <CardTitle className="text-foreground">Ready to launch</CardTitle>
            <p className="text-sm text-muted-foreground">
              {airdrop.recipientCount} recipients, {airdrop.totalAmount.toLocaleString()} {airdrop.assetType} total.
            </p>
          </CardHeader>
          <CardContent>
            <Button onClick={() => runAction(() => launchAirdrop(id))} disabled={actionLoading}>
              {actionLoading ? "Launching…" : "Launch airdrop"}
            </Button>
          </CardContent>
        </Card>
      )}

      {LIVE_STATUSES.has(airdrop.status) && (
        <Card className="mb-8">
          <CardHeader><CardTitle className="text-foreground">Processing…</CardTitle></CardHeader>
          <CardContent>
            <Progress value={pct} className="mb-2" />
            <p className="font-mono text-sm text-muted-foreground">
              {done} / {recipients.length} processed{failed > 0 && ` · ${failed} failed`}
            </p>
          </CardContent>
        </Card>
      )}

      {CANCELLABLE_STATUSES.has(airdrop.status) && (
        <Button
          variant="link"
          onClick={() => runAction(() => cancelAirdrop(id))}
          disabled={actionLoading}
          className="mb-8 h-auto p-0 text-destructive"
        >
          Cancel airdrop
        </Button>
      )}

      {recipients.length > 0 && (
        <section className="mb-8">
          <h2 className="mb-3 text-sm font-medium text-muted-foreground">Recipients</h2>
          <Card className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Address</TableHead>
                  <TableHead className="text-right">Amount</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Note</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {recipients.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell className="font-mono text-xs">{r.recipientAddress}</TableCell>
                    <TableCell className="text-right font-mono">{r.amount.toLocaleString()}</TableCell>
                    <TableCell><StatusBadge status={r.status} /></TableCell>
                    <TableCell className="text-xs text-muted-foreground">{r.errorMessage ?? ""}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>
        </section>
      )}

      {events.length > 0 && (
        <section>
          <h2 className="mb-3 text-sm font-medium text-muted-foreground">Activity</h2>
          <ol className="space-y-3 border-l border-border pl-4">
            {events.map((e) => (
              <li key={e.id}>
                <p className="text-sm">
                  <span className="font-medium">{formatEventType(e.eventType)}</span>
                  {e.message && <span className="text-muted-foreground"> — {e.message}</span>}
                </p>
                <p className="font-mono text-xs text-muted-foreground">{new Date(e.createdAt).toLocaleString()}</p>
              </li>
            ))}
          </ol>
        </section>
      )}
    </div>
  );
}

function MiniStat({ label, value }: { label: string; value: string }) {
  return (
    <Card>
      <CardContent className="p-4">
        <p className="text-xs text-muted-foreground">{label}</p>
        <p className="mt-0.5 truncate font-mono font-medium">{value}</p>
      </CardContent>
    </Card>
  );
}

function formatEventType(type: string): string {
  return type.toLowerCase().split("_").map((w) => w[0].toUpperCase() + w.slice(1)).join(" ");
}
