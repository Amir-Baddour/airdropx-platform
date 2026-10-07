import { clearTokens, getAccessToken, getRefreshToken, setAccessToken, setTokens } from "./auth";

const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  status: number;
  code?: string;
  fieldErrors?: Record<string, string>;

  constructor(status: number, message: string, code?: string, fieldErrors?: Record<string, string>) {
    super(message);
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

// --- Types (mirroring the backend response records) -----------------------------------------------

export interface UserSummary {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  address: string | null;
  role: string;
  companyId: string | null;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  user: UserSummary;
}

export interface CompanyResponse {
  id: string;
  name: string;
  legalName: string | null;
  email: string;
  phone: string | null;
  website: string | null;
  description: string | null;
  country: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface AirdropResponse {
  id: string;
  name: string;
  description: string | null;
  assetType: string;
  totalAmount: number;
  recipientCount: number;
  status: string;
  scheduledAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface RecipientResponse {
  id: string;
  recipientAddress: string;
  amount: number;
  status: string;
  errorMessage: string | null;
  processedAt: string | null;
}

export interface AirdropEventResponse {
  id: string;
  eventType: string;
  message: string | null;
  createdAt: string;
}

export interface LaunchResponse {
  airdropId: string;
  jobId: string;
  status: string;
}

export interface DashboardSummary {
  totalAirdrops: number;
  byStatus: Record<string, number>;
  totalRecipientsCompleted: number;
  totalRecipientsFailed: number;
  recentAirdrops: { id: string; name: string; status: string; updatedAt: string }[];
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// --- Core request logic with one-shot refresh-and-retry on 401 -------------------------------------

async function parseErrorBody(res: Response): Promise<ApiError> {
  try {
    const body = await res.json();
    return new ApiError(res.status, body.message ?? res.statusText, body.code, body.fieldErrors);
  } catch {
    return new ApiError(res.status, res.statusText || "Request failed");
  }
}

async function doFetch(path: string, options: RequestInit, token: string | null): Promise<Response> {
  const headers = new Headers(options.headers);
  headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);
  return fetch(`${BASE_URL}${path}`, { ...options, headers });
}

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return null;

  const res = await fetch(`${BASE_URL}/api/v1/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });
  if (!res.ok) {
    clearTokens();
    return null;
  }
  const data: AuthResponse = await res.json();
  // Refresh tokens rotate server-side (see JwtService/AuthService) — persist the new pair, not just the access token.
  setTokens(data.accessToken, data.refreshToken);
  return data.accessToken;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getAccessToken();
  let res = await doFetch(path, options, token);

  if (res.status === 401 && token) {
    const newToken = await refreshAccessToken();
    if (newToken) {
      setAccessToken(newToken);
      res = await doFetch(path, options, newToken);
    }
  }

  if (!res.ok) {
    throw await parseErrorBody(res);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return res.json() as Promise<T>;
}

// --- Auth --------------------------------------------------------------------------------------------

export function register(input: {
  companyName: string; companyEmail: string; email: string; password: string; firstName: string; lastName: string;
}) {
  return request<AuthResponse>("/api/v1/auth/register", { method: "POST", body: JSON.stringify(input) });
}

export function login(input: { email: string; password: string }) {
  return request<AuthResponse>("/api/v1/auth/login", { method: "POST", body: JSON.stringify(input) });
}

export function logout() {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return Promise.resolve();
  return request<void>("/api/v1/auth/logout", { method: "POST", body: JSON.stringify({ refreshToken }) });
}

export function me() {
  return request<UserSummary>("/api/v1/auth/me");
}

export function updateProfile(input: { firstName: string; lastName: string; phone: string; address: string }) {
  return request<UserSummary>("/api/v1/auth/me", { method: "PUT", body: JSON.stringify(input) });
}

// --- Company -----------------------------------------------------------------------------------------

export function getMyCompany() {
  return request<CompanyResponse>("/api/v1/user/company");
}

export function updateCompany(input: {
  name: string; legalName: string; phone: string; website: string; description: string; country: string;
}) {
  return request<CompanyResponse>("/api/v1/user/company", { method: "PUT", body: JSON.stringify(input) });
}

// --- Dashboard ---------------------------------------------------------------------------------------

export function getDashboard() {
  return request<DashboardSummary>("/api/v1/user/dashboard");
}

// --- Airdrops ----------------------------------------------------------------------------------------

export function listAirdrops(page = 0, size = 20) {
  return request<PageResponse<AirdropResponse>>(`/api/v1/user/airdrops?page=${page}&size=${size}`);
}

export function getAirdrop(id: string) {
  return request<AirdropResponse>(`/api/v1/user/airdrops/${id}`);
}

export function createAirdrop(input: { name: string; description: string; assetType: string }) {
  return request<AirdropResponse>("/api/v1/user/airdrops", { method: "POST", body: JSON.stringify(input) });
}

export function addRecipients(id: string, recipients: { recipientAddress: string; amount: number }[]) {
  return request<AirdropResponse>(`/api/v1/user/airdrops/${id}/recipients`, {
    method: "POST",
    body: JSON.stringify({ recipients }),
  });
}

export function listRecipients(id: string, page = 0, size = 50) {
  return request<PageResponse<RecipientResponse>>(`/api/v1/user/airdrops/${id}/recipients?page=${page}&size=${size}`);
}

export function validateAirdrop(id: string) {
  return request<AirdropResponse>(`/api/v1/user/airdrops/${id}/validate`, { method: "POST" });
}

export function launchAirdrop(id: string) {
  // A fresh key per click: this is a NEW user-initiated launch, not a retry of a previous one. The
  // idempotency protection is for the browser/network layer silently retrying the request Claude sent,
  // not for stopping a person from launching a second, different time.
  const idempotencyKey = crypto.randomUUID();
  return request<LaunchResponse>(`/api/v1/user/airdrops/${id}/launch`, {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
  });
}

export function cancelAirdrop(id: string) {
  return request<AirdropResponse>(`/api/v1/user/airdrops/${id}/cancel`, { method: "POST" });
}

export function listEvents(id: string, page = 0, size = 50) {
  return request<PageResponse<AirdropEventResponse>>(`/api/v1/user/airdrops/${id}/events?page=${page}&size=${size}`);
}
