// Deliberately minimal — no auth library, no context provider gymnastics. This is a demo frontend for a
// backend-focused portfolio project; the interesting engineering lives in the API, not here. localStorage
// is fine for a real app the person runs themselves (this isn't a Claude Artifact, so that restriction
// doesn't apply) but every access is guarded for SSR, since Next.js renders this module's callers on the
// server too, where `window` doesn't exist.

const ACCESS_KEY = "airdropx_access_token";
const REFRESH_KEY = "airdropx_refresh_token";

export function getAccessToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(ACCESS_KEY);
}

export function getRefreshToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(REFRESH_KEY);
}

export function setTokens(accessToken: string, refreshToken: string) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(ACCESS_KEY, accessToken);
  window.localStorage.setItem(REFRESH_KEY, refreshToken);
}

export function setAccessToken(accessToken: string) {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(ACCESS_KEY, accessToken);
}

export function clearTokens() {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(ACCESS_KEY);
  window.localStorage.removeItem(REFRESH_KEY);
}

export function isLoggedIn(): boolean {
  return getAccessToken() !== null;
}
