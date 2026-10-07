"use client";

import { useEffect, useState } from "react";
import { me, updateProfile, UserSummary, ApiError } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";

export default function ProfilePage() {
  const [user, setUser] = useState<UserSummary | null>(null);
  const [form, setForm] = useState({ firstName: "", lastName: "", phone: "", address: "" });
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    me()
      .then((u) => {
        setUser(u);
        setForm({ firstName: u.firstName, lastName: u.lastName, phone: u.phone ?? "", address: u.address ?? "" });
      })
      .catch((e) => setLoadError(e instanceof ApiError ? e.message : "Failed to load profile"));
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
      const updated = await updateProfile(form);
      setUser(updated);
      setSaved(true);
    } catch (err) {
      setSaveError(err instanceof ApiError ? err.message : "Failed to save changes");
    } finally {
      setSaving(false);
    }
  }

  if (loadError) return <p className="text-destructive">{loadError}</p>;
  if (!user) return <p className="text-muted-foreground">Loading…</p>;

  return (
    <div className="max-w-lg">
      <h1 className="mb-1 text-2xl font-semibold">Your profile</h1>
      <p className="mb-6 text-muted-foreground">Personal details for your own account — separate from the company profile.</p>

      <Card>
        <CardHeader>
          <CardTitle className="text-foreground">Account</CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-2 gap-4 pt-0">
          <div>
            <p className="text-xs text-muted-foreground">Email</p>
            <p className="text-sm">{user.email}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Role</p>
            <p className="text-sm">{user.role}</p>
          </div>
        </CardContent>
      </Card>

      <p className="my-4 text-xs text-muted-foreground">
        Email isn&apos;t editable here — changing a login address safely needs re-verification, which needs
        email sending, which isn&apos;t wired up yet. See README &gt; Roadmap.
      </p>

      <Card>
        <CardHeader>
          <CardTitle className="text-foreground">Editable details</CardTitle>
        </CardHeader>
        <CardContent className="pt-0">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <Label>First name</Label>
                <Input required value={form.firstName} onChange={update("firstName")} />
              </div>
              <div className="space-y-1.5">
                <Label>Last name</Label>
                <Input required value={form.lastName} onChange={update("lastName")} />
              </div>
            </div>
            <div className="space-y-1.5">
              <Label>Phone</Label>
              <Input value={form.phone} onChange={update("phone")} />
            </div>
            <div className="space-y-1.5">
              <Label>Address</Label>
              <Textarea rows={3} value={form.address} onChange={update("address")} />
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
