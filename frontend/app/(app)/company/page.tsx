"use client";

import { useEffect, useState } from "react";
import { getMyCompany, updateCompany, CompanyResponse, ApiError } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";

export default function CompanyPage() {
  const [company, setCompany] = useState<CompanyResponse | null>(null);
  const [form, setForm] = useState({ name: "", legalName: "", phone: "", website: "", description: "", country: "" });
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    getMyCompany()
      .then((c) => {
        setCompany(c);
        setForm({
          name: c.name,
          legalName: c.legalName ?? "",
          phone: c.phone ?? "",
          website: c.website ?? "",
          description: c.description ?? "",
          country: c.country ?? "",
        });
      })
      .catch((e) => setLoadError(e instanceof ApiError ? e.message : "Failed to load company"));
  }, []);

  function update(field: keyof typeof form) {
    return (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      setSaved(false);
      setForm({ ...form, [field]: e.target.value });
    };
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSaveError(null);
    setSaving(true);
    try {
      const updated = await updateCompany(form);
      setCompany(updated);
      setSaved(true);
    } catch (err) {
      setSaveError(err instanceof ApiError ? err.message : "Failed to save changes");
    } finally {
      setSaving(false);
    }
  }

  if (loadError) return <p className="text-destructive">{loadError}</p>;
  if (!company) return <p className="text-muted-foreground">Loading…</p>;

  return (
    <div className="max-w-lg">
      <h1 className="mb-1 text-2xl font-semibold">Company profile</h1>
      <p className="mb-6 text-muted-foreground">Manage the details for {company.name}.</p>

      <Card>
        <CardHeader>
          <CardTitle className="text-foreground">Account</CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-2 gap-4 pt-0">
          <div>
            <p className="text-xs text-muted-foreground">Email</p>
            <p className="text-sm">{company.email}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Status</p>
            <p className="text-sm">{company.status}</p>
          </div>
        </CardContent>
      </Card>

      <Separator className="my-6" />

      <Card>
        <CardHeader>
          <CardTitle className="text-foreground">Editable details</CardTitle>
        </CardHeader>
        <CardContent className="pt-0">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-1.5">
              <Label>Name</Label>
              <Input required value={form.name} onChange={update("name")} />
            </div>
            <div className="space-y-1.5">
              <Label>Legal name</Label>
              <Input value={form.legalName} onChange={update("legalName")} placeholder="Acme Inc. Ltd." />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <Label>Phone</Label>
                <Input value={form.phone} onChange={update("phone")} />
              </div>
              <div className="space-y-1.5">
                <Label>Country</Label>
                <Input value={form.country} onChange={update("country")} />
              </div>
            </div>
            <div className="space-y-1.5">
              <Label>Website</Label>
              <Input value={form.website} onChange={update("website")} placeholder="https://" />
            </div>
            <div className="space-y-1.5">
              <Label>Description</Label>
              <Textarea rows={3} value={form.description} onChange={update("description")} />
            </div>

            {saveError && <p className="text-sm text-destructive">{saveError}</p>}
            {saved && <p className="text-sm text-status-success">Saved.</p>}

            <Button type="submit" disabled={saving}>
              {saving ? "Saving…" : "Save changes"}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
