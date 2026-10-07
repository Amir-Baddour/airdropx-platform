"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Copy, Trash2 } from "lucide-react";
import {
  AirdropResponse,
  ApiError,
  ClaimResponse,
  ClaimSettings,
  TaskResponse,
  approveClaim,
  createTask,
  deleteTask,
  getAirdrop,
  getClaimSettings,
  listClaims,
  listTasks,
  rejectClaim,
  updateClaimSettings,
} from "@/lib/api";
import StatusBadge from "@/components/StatusBadge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";

const FILTERS = ["PENDING", "APPROVED", "REJECTED"];

export default function ClaimsAdminPage() {
  const { id } = useParams<{ id: string }>();
  const [airdrop, setAirdrop] = useState<AirdropResponse | null>(null);
  const [settings, setSettings] = useState<ClaimSettings | null>(null);
  const [tasks, setTasks] = useState<TaskResponse[]>([]);
  const [claims, setClaims] = useState<ClaimResponse[]>([]);
  const [filter, setFilter] = useState("PENDING");
  const [amount, setAmount] = useState("");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [proofRequired, setProofRequired] = useState(true);
  const [error, setError] = useState("");
  const [copied, setCopied] = useState(false);

  const fail = (e: unknown) => setError(e instanceof ApiError ? e.message : "Something went wrong.");

  const refresh = useCallback(async () => {
    try {
      const [a, s, t] = await Promise.all([getAirdrop(id), getClaimSettings(id), listTasks(id)]);
      setAirdrop(a);
      setSettings(s);
      setTasks(t);
      setAmount(s.claimAmount != null ? String(s.claimAmount) : "");
    } catch (e) {
      fail(e);
    }
  }, [id]);

  const loadClaims = useCallback(async () => {
    try {
      setClaims((await listClaims(id, filter)).items);
    } catch (e) {
      fail(e);
    }
  }, [id, filter]);

  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { refresh(); }, [refresh]);
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { loadClaims(); }, [loadClaims]);

  async function act(fn: () => Promise<unknown>, reload: () => Promise<void> = refresh) {
    setError("");
    try {
      await fn();
      await reload();
    } catch (e) {
      fail(e);
    }
  }

  if (!airdrop || !settings) {
    return error ? <p className="text-destructive">{error}</p> : <p className="text-muted-foreground">Loading…</p>;
  }

  const editable = airdrop.status === "DRAFT";
  const link = typeof window !== "undefined" ? `${window.location.origin}/claim/${id}` : "";

  return (
    <div className="max-w-3xl space-y-8">
      <div>
        <Link href={`/airdrops/${id}`} className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-primary">
          <ArrowLeft size={14} /> {airdrop.name}
        </Link>
        <h1 className="text-2xl font-semibold">Claims</h1>
        <p className="text-sm text-muted-foreground">
          Let people claim this airdrop. Approved claims become recipients. Only possible while the airdrop is DRAFT.
        </p>
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}

      <Card>
        <CardHeader><CardTitle className="text-foreground">Settings</CardTitle></CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-end gap-3">
            <div className="space-y-2">
              <Label htmlFor="amount">Amount per approved claim ({airdrop.assetType})</Label>
              <Input id="amount" type="number" min="0" step="any" value={amount} onChange={(e) => setAmount(e.target.value)} disabled={!editable} />
            </div>
            <Button
              disabled={!editable}
              onClick={() => act(() => updateClaimSettings(id, { claimsOpen: settings.claimsOpen, claimAmount: amount ? Number(amount) : null }))}
            >
              Save amount
            </Button>
          </div>
          <div className="flex items-center gap-3">
            <StatusBadge status={settings.claimsOpen ? "OPEN" : "CLOSED"} />
            <Button
              variant="outline"
              disabled={!editable}
              onClick={() => act(() => updateClaimSettings(id, { claimsOpen: !settings.claimsOpen, claimAmount: amount ? Number(amount) : null }))}
            >
              {settings.claimsOpen ? "Close claiming" : "Open claiming"}
            </Button>
            <span className="text-xs text-muted-foreground">Opening needs at least one task and an amount.</span>
          </div>
          {settings.claimsOpen && (
            <div className="flex items-center gap-2">
              <code className="flex-1 truncate rounded bg-muted px-2 py-2 text-xs">{link}</code>
              <Button
                variant="outline"
                onClick={() => { navigator.clipboard.writeText(link); setCopied(true); setTimeout(() => setCopied(false), 1500); }}
              >
                <Copy size={14} className="mr-1" /> {copied ? "Copied" : "Copy link"}
              </Button>
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle className="text-foreground">Tasks</CardTitle></CardHeader>
        <CardContent className="space-y-4">
          {tasks.length === 0 && <p className="text-sm text-muted-foreground">No tasks yet.</p>}
          {tasks.map((t) => (
            <div key={t.id} className="flex items-start justify-between gap-3 rounded-md border p-3">
              <div>
                <p className="font-medium">{t.title} {t.proofRequired && <span className="text-xs text-muted-foreground">(proof required)</span>}</p>
                {t.description && <p className="text-sm text-muted-foreground">{t.description}</p>}
              </div>
              {editable && (
                <Button variant="ghost" size="sm" aria-label="Delete task" onClick={() => act(() => deleteTask(id, t.id))}>
                  <Trash2 size={14} />
                </Button>
              )}
            </div>
          ))}
          {editable && (
            <form
              className="space-y-3 border-t pt-4"
              onSubmit={(e) => {
                e.preventDefault();
                act(async () => {
                  await createTask(id, { title, description, proofRequired });
                  setTitle(""); setDescription("");
                });
              }}
            >
              <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Task title, e.g. Follow us on X" required />
              <Input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Description (optional)" />
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={proofRequired} onChange={(e) => setProofRequired(e.target.checked)} /> Proof required
              </label>
              <Button type="submit">Add task</Button>
            </form>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-foreground">Review queue</CardTitle>
          <div className="flex gap-2 pt-2">
            {FILTERS.map((f) => (
              <Button key={f} size="sm" variant={filter === f ? "default" : "outline"} onClick={() => setFilter(f)}>{f}</Button>
            ))}
          </div>
        </CardHeader>
        <CardContent className="space-y-3">
          {claims.length === 0 && <p className="text-sm text-muted-foreground">No {filter.toLowerCase()} claims.</p>}
          {claims.map((c) => (
            <div key={c.id} className="space-y-2 rounded-md border p-3">
              <div className="flex items-center justify-between gap-2">
                <code className="truncate text-xs">{c.claimantAddress}</code>
                <StatusBadge status={c.status} />
              </div>
              {c.submissions.map((s) => (
                <p key={s.taskId} className="text-sm">
                  <span className="text-muted-foreground">{s.taskTitle}:</span> {s.proof || "—"}
                </p>
              ))}
              {c.reviewNote && <p className="text-xs text-muted-foreground">Note: {c.reviewNote}</p>}
              {c.status === "PENDING" && (
                <div className="flex gap-2">
                  <Button size="sm" disabled={!editable} onClick={() => act(() => approveClaim(id, c.id), async () => { await refresh(); await loadClaims(); })}>Approve</Button>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => {
                      const note = window.prompt("Reason (optional)") ?? "";
                      act(() => rejectClaim(id, c.id, note), async () => { await refresh(); await loadClaims(); });
                    }}
                  >
                    Reject
                  </Button>
                </div>
              )}
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}
