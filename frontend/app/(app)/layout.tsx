"use client";

import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import Link from "next/link";
import { LayoutGrid, Rocket, Building2, User, LogOut } from "lucide-react";
import { isLoggedIn, clearTokens } from "@/lib/auth";
import { logout as apiLogout, me, UserSummary } from "@/lib/api";
import { Separator } from "@/components/ui/separator";
import { Button } from "@/components/ui/button";

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [user, setUser] = useState<UserSummary | null>(null);
  const [checked, setChecked] = useState(false);

  useEffect(() => {
    if (!isLoggedIn()) {
      router.replace("/login");
      return;
    }
    me()
      .then(setUser)
      .catch(() => router.replace("/login"))
      .finally(() => setChecked(true));
  }, [router]);

  async function handleLogout() {
    try {
      await apiLogout();
    } finally {
      clearTokens();
      router.push("/login");
    }
  }

  if (!checked) {
    return <div className="flex min-h-screen items-center justify-center text-muted-foreground">Loading…</div>;
  }

  const navItems = [
    { href: "/dashboard", label: "Dashboard", icon: LayoutGrid },
    { href: "/airdrops", label: "Airdrops", icon: Rocket },
    { href: "/company", label: "Company", icon: Building2 },
    { href: "/profile", label: "Profile", icon: User },
  ];

  return (
    <div className="flex min-h-screen">
      <aside className="flex w-60 shrink-0 flex-col border-r border-border bg-card px-4 py-6">
        <span className="mb-8 px-2 text-lg font-semibold tracking-tight">AirdropX</span>

        <nav className="flex flex-1 flex-col gap-1">
          {navItems.map((item) => {
            const active = pathname === item.href || pathname.startsWith(item.href + "/");
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors ${
                  active ? "bg-accent text-accent-foreground" : "text-muted-foreground hover:bg-muted hover:text-foreground"
                }`}
              >
                <Icon size={16} strokeWidth={2} />
                {item.label}
              </Link>
            );
          })}
        </nav>

        <Separator className="mb-4" />
        <div>
          {user && <p className="truncate px-2 text-sm text-muted-foreground">{user.email}</p>}
          <Button
            variant="ghost"
            size="sm"
            onClick={handleLogout}
            className="mt-2 w-full justify-start gap-2 px-3 text-muted-foreground"
          >
            <LogOut size={16} strokeWidth={2} />
            Log out
          </Button>
        </div>
      </aside>

      <main className="flex-1 px-8 py-8">{children}</main>
    </div>
  );
}
