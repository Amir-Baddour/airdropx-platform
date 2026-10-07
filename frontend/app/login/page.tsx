"use client";

import { Suspense, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { login, ApiError } from "@/lib/api";
import { setTokens } from "@/lib/auth";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export default function LoginPage() {
  // useSearchParams needs a Suspense boundary so the page can still be statically prerendered.
  return (
    <Suspense fallback={null}>
      <LoginForm />
    </Suspense>
  );
}

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const justRegistered = params.get("registered") === "1";
  const [email, setEmail] = useState(justRegistered ? params.get("email") ?? "" : "");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await login({ email, password });
      setTokens(res.accessToken, res.refreshToken);
      router.push("/dashboard");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Try again.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="mb-8">
          <span className="text-xl font-semibold tracking-tight">AirdropX</span>
        </div>

        <h1 className="mb-1 text-2xl font-semibold">Log in</h1>
        <p className="mb-6 text-muted-foreground">Manage your campaigns and see live progress.</p>

        {justRegistered && (
          <p className="mb-4 rounded-md border border-primary/30 bg-primary/5 px-3 py-2 text-sm">
            Account created. Log in to continue.
          </p>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="email">Email</Label>
            <Input id="email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@company.com" />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="password">Password</Label>
            <Input id="password" type="password" required value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" />
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}

          <Button type="submit" disabled={loading} className="w-full">
            {loading ? "Logging in…" : "Log in"}
          </Button>
        </form>

        <p className="mt-6 text-sm text-muted-foreground">
          No account yet?{" "}
          <Link href="/register" className="font-medium text-primary">
            Register your company
          </Link>
        </p>
      </div>
    </div>
  );
}
