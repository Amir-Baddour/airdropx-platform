"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { register, ApiError } from "@/lib/api";
import { setTokens } from "@/lib/auth";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export default function RegisterPage() {
  const router = useRouter();
  const [form, setForm] = useState({
    companyName: "", companyEmail: "", firstName: "", lastName: "", email: "", password: "",
  });
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  function update(field: keyof typeof form) {
    return (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [field]: e.target.value });
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await register(form);
      setTokens(res.accessToken, res.refreshToken);
      router.push("/dashboard");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Try again.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm">
        <div className="mb-8">
          <span className="text-xl font-semibold tracking-tight">AirdropX</span>
        </div>

        <h1 className="mb-1 text-2xl font-semibold">Register your company</h1>
        <p className="mb-6 text-muted-foreground">You&apos;ll be the owner account for this company.</p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <Field label="Company name" value={form.companyName} onChange={update("companyName")} placeholder="Acme Inc." />
          <Field label="Company email" type="email" value={form.companyEmail} onChange={update("companyEmail")} placeholder="ops@acme.io" />
          <div className="grid grid-cols-2 gap-3">
            <Field label="First name" value={form.firstName} onChange={update("firstName")} placeholder="Ada" />
            <Field label="Last name" value={form.lastName} onChange={update("lastName")} placeholder="Lovelace" />
          </div>
          <Field label="Your email" type="email" value={form.email} onChange={update("email")} placeholder="ada@acme.io" />
          <Field label="Password" type="password" value={form.password} onChange={update("password")} placeholder="At least 8 characters" />

          {error && <p className="text-sm text-destructive">{error}</p>}

          <Button type="submit" disabled={loading} className="w-full">
            {loading ? "Creating account…" : "Create account"}
          </Button>
        </form>

        <p className="mt-6 text-sm text-muted-foreground">
          Already have an account?{" "}
          <Link href="/login" className="font-medium text-primary">Log in</Link>
        </p>
      </div>
    </div>
  );
}

function Field({ label, type = "text", value, onChange, placeholder }: {
  label: string; type?: string; value: string;
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void; placeholder?: string;
}) {
  return (
    <div className="space-y-1.5">
      <Label>{label}</Label>
      <Input type={type} required value={value} onChange={onChange} placeholder={placeholder} />
    </div>
  );
}
