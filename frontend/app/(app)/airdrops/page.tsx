"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Plus } from "lucide-react";
import { listAirdrops, AirdropResponse, ApiError } from "@/lib/api";
import StatusBadge from "@/components/StatusBadge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from "@/components/ui/table";

export default function AirdropsListPage() {
  const [airdrops, setAirdrops] = useState<AirdropResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listAirdrops()
      .then((res) => setAirdrops(res.items))
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load airdrops"));
  }, []);

  return (
    <div className="max-w-5xl">
      <div className="mb-8 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Airdrops</h1>
        <Button asChild>
          <Link href="/airdrops/new">
            <Plus size={16} />
            New airdrop
          </Link>
        </Button>
      </div>

      {error && <p className="text-destructive">{error}</p>}
      {!error && !airdrops && <p className="text-muted-foreground">Loading…</p>}

      {airdrops && airdrops.length === 0 && (
        <Card className="border-dashed px-4 py-10 text-center">
          <p className="text-muted-foreground">No airdrops yet.</p>
          <Link href="/airdrops/new" className="mt-2 inline-block text-sm font-medium text-primary">
            Create your first airdrop
          </Link>
        </Card>
      )}

      {airdrops && airdrops.length > 0 && (
        <Card className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Asset</TableHead>
                <TableHead className="text-right">Recipients</TableHead>
                <TableHead className="text-right">Total amount</TableHead>
                <TableHead>Updated</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {airdrops.map((a) => (
                <TableRow key={a.id}>
                  <TableCell>
                    <Link href={`/airdrops/${a.id}`} className="font-medium hover:text-primary">
                      {a.name}
                    </Link>
                  </TableCell>
                  <TableCell><StatusBadge status={a.status} /></TableCell>
                  <TableCell className="font-mono text-muted-foreground">{a.assetType}</TableCell>
                  <TableCell className="text-right font-mono">{a.recipientCount}</TableCell>
                  <TableCell className="text-right font-mono">{a.totalAmount.toLocaleString()}</TableCell>
                  <TableCell className="text-muted-foreground">{new Date(a.updatedAt).toLocaleString()}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}
    </div>
  );
}
