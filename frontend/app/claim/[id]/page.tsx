"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import {
  ApiError,
  ClaimStatusResponse,
  PublicAirdrop,
  getClaimStatus,
  getPublicAirdrop,
  submitClaim,
} from "@/lib/api";
import StatusBadge from "@/components/StatusBadge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";

// Public page: no login. Anyone with the link can claim once per address; the company reviews manually.
export default function ClaimPage() {
  const { id } = useParams<{ id: string }>();
  const [airdrop, setAirdrop] = useState<PublicAirdrop | null>(null);
  const [loadError, setLoadError] = useState("");
  const [loading, setLoading] = useState(true);

  const [address, setAddress] = useState("");
  const [proofs, setProofs] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState<ClaimStatusResponse | null>(null);

  useEffect(() => {
    getPublicAirdrop(id)
      .then(setAirdrop)
      .catch((e) =>
        setLoadError(
          e instanceof ApiError && e.status === 404
            ? "This claim page is not open. The link may be wrong or claiming has closed."
            : "Could not load this page. Try again later."
        )
      )
      .finally(() => setLoading(false));
  }, [id]);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!airdrop) return;
    setError("");
    setSubmitting(true);
    try {
      const res = await submitClaim(id, {
        address: address.trim(),
        submissions: airdrop.tasks.map((t) => ({ taskId: t.id, proof: (proofs[t.id] ?? "").trim() })),
      });
      setResult(res);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setError("This address has already submitted a claim. Use “Check status” below.");
      } else if (err instanceof ApiError && err.status === 429) {
        setError("Too many attempts. Wait a minute and try again.");
      } else {
        setError(err instanceof ApiError ? err.message : "Submission failed.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function checkStatus() {
    setError("");
    if (!address.trim()) {
      setError("Enter your address first.");
      return;
    }
    try {
      setResult(await getClaimStatus(id, address.trim()));
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 404
          ? "No claim found for this address."
          : err instanceof ApiError
            ? err.message
            : "Could not check status."
      );
    }
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-xl flex-col justify-center px-4 py-10">
      {loading && <p className="text-center text-muted-foreground">Loading…</p>}
      {loadError && <p className="text-center text-destructive">{loadError}</p>}

      {airdrop && (
        <>
          <p className="text-sm text-muted-foreground">{airdrop.companyName}</p>
          <h1 className="mb-1 text-2xl font-semibold">{airdrop.name}</h1>
          {airdrop.description && <p className="mb-4 text-muted-foreground">{airdrop.description}</p>}
          <p className="mb-6 text-sm">
            Reward:{" "}
            <span className="font-mono font-medium">
              {airdrop.claimAmount != null ? `${airdrop.claimAmount.toLocaleString()} ${airdrop.assetType}` : airdrop.assetType}
            </span>
          </p>

          {result ? (
            <Card>
              <CardHeader>
                <CardTitle className="text-foreground">Your claim</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="flex items-center gap-2">
                  <span className="text-sm text-muted-foreground">Status</span>
                  <StatusBadge status={result.status} />
                </div>
                {result.status === "PENDING" && (
                  <p className="text-sm text-muted-foreground">Submitted. The team will review it; check back with the same address.</p>
                )}
                {result.status === "APPROVED" && (
                  <p className="text-sm">Approved. You are on the recipient list and will receive the distribution when the airdrop runs.</p>
                )}
                {result.reviewNote && <p className="text-sm text-muted-foreground">Note: {result.reviewNote}</p>}
                <Button variant="outline" onClick={() => setResult(null)}>Back</Button>
              </CardContent>
            </Card>
          ) : (
            <form onSubmit={onSubmit} className="space-y-5">
              <div className="space-y-2">
                <Label htmlFor="address">Your wallet address</Label>
                <Input id="address" value={address} onChange={(e) => setAddress(e.target.value)} placeholder="0x…" className="font-mono" required />
              </div>

              {airdrop.tasks.length > 0 && <h2 className="text-sm font-medium">Tasks</h2>}
              {airdrop.tasks.map((t, i) => (
                <Card key={t.id}>
                  <CardContent className="space-y-2 pt-4">
                    <p className="font-medium">{i + 1}. {t.title}</p>
                    {t.description && <p className="text-sm text-muted-foreground">{t.description}</p>}
                    <Textarea
                      value={proofs[t.id] ?? ""}
                      onChange={(e) => setProofs((p) => ({ ...p, [t.id]: e.target.value }))}
                      placeholder={t.proofRequired ? "Proof (required): link or username" : "Proof (optional)"}
                      required={t.proofRequired}
                      rows={2}
                    />
                  </CardContent>
                </Card>
              ))}

              {error && <p className="text-sm text-destructive">{error}</p>}
              <div className="flex gap-3">
                <Button type="submit" disabled={submitting}>{submitting ? "Submitting…" : "Submit claim"}</Button>
                <Button type="button" variant="outline" onClick={checkStatus}>Check status</Button>
              </div>
            </form>
          )}
        </>
      )}
    </main>
  );
}
