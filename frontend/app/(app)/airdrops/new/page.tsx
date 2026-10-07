"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { createAirdrop, ApiError } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";

export default function NewAirdropPage() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [assetType, setAssetType] = useState("USDT");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const airdrop = await createAirdrop({ name, description, assetType });
      router.push(`/airdrops/${airdrop.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to create airdrop");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="max-w-lg">
      <h1 className="mb-1 text-2xl font-semibold">New airdrop</h1>
      <p className="mb-6 text-muted-foreground">Starts as a draft — you&apos;ll add recipients next.</p>

      <Card>
        <CardContent className="pt-5">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <Label>Name</Label>
              <Input required value={name} onChange={(e) => setName(e.target.value)} placeholder="Q4 Community Drop" />
            </div>

            <div className="space-y-1.5">
              <Label>Description</Label>
              <Textarea value={description} onChange={(e) => setDescription(e.target.value)} placeholder="What is this campaign for?" rows={3} />
            </div>

            <div className="space-y-1.5">
              <Label>Asset type</Label>
              <Input required value={assetType} onChange={(e) => setAssetType(e.target.value)} placeholder="USDT" className="font-mono" />
              <p className="text-xs text-muted-foreground">Free-text label only — no real asset moves. See README.</p>
            </div>

            {error && <p className="text-sm text-destructive">{error}</p>}

            <Button type="submit" disabled={loading}>
              {loading ? "Creating…" : "Create draft"}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
