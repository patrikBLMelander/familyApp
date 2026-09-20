import { API_BASE_URL } from "../config";

/** Returned on activate/login: the portal token plus a little context. */
export type AffiliateSession = {
  token: string;
  referralCode: string;
  name: string;
};

/** One month in the affiliate's time series. */
export type MonthPoint = { month: string; earned: number; referrals: number };

/** One payout in the affiliate's history. */
export type PayoutPoint = { paidAt: string; method: string; amount: number; currency: string };

/** One referred family, anonymised (no id/name/contact). */
export type ReferralRow = { joinedMonthsAgo: number; status: string; earningCommission: boolean };

/** The affiliate's analytics dashboard. */
export type AffiliateStats = {
  name: string;
  referralCode: string;
  referralLink: string;
  commissionPct: number;
  referralCount: number;
  totalEarned: number;
  pending: number;
  payable: number;
  paidOut: number;
  thisMonthEarned: number;
  monthly: MonthPoint[];
  payouts: PayoutPoint[];
};

/** The affiliate's own dashboard numbers. */
export type AffiliateSelf = {
  name: string;
  referralCode: string;
  referralLink: string;
  commissionPct: number;
  referralCount: number;
  pending: number;
  approved: number;
  paidOut: number;
};

/** One row of the admin's affiliate list. */
export type AffiliateAdminRow = {
  id: string;
  name: string;
  email: string;
  referralCode: string;
  status: string;
  referralCount: number;
  payable: number;
  pending: number;
  paidOut: number;
};

async function parse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let message = `Något gick fel (${response.status})`;
    try {
      const body = await response.json();
      if (body?.message) message = body.message;
      else if (typeof body === "string") message = body;
    } catch {
      /* keep the default */
    }
    throw new Error(message);
  }
  return (await response.json()) as T;
}

const JSON_HEADERS = { "Content-Type": "application/json" };

/** Invited affiliate sets a password. Only works if an admin created the invite. */
export async function activateAffiliate(email: string, password: string): Promise<AffiliateSession> {
  const res = await fetch(`${API_BASE_URL}/affiliate-auth/activate`, {
    method: "POST",
    headers: JSON_HEADERS,
    body: JSON.stringify({ email, password }),
  });
  return parse<AffiliateSession>(res);
}

/** Affiliate logs in; returns a portal token. */
export async function loginAffiliate(email: string, password: string): Promise<AffiliateSession> {
  const res = await fetch(`${API_BASE_URL}/affiliate-auth/login`, {
    method: "POST",
    headers: JSON_HEADERS,
    body: JSON.stringify({ email, password }),
  });
  return parse<AffiliateSession>(res);
}

/** The affiliate's own dashboard, authenticated by their portal token. */
export async function getAffiliateSelf(token: string): Promise<AffiliateSelf> {
  const res = await fetch(`${API_BASE_URL}/affiliate/me`, {
    headers: { "X-Affiliate-Token": token },
  });
  return parse<AffiliateSelf>(res);
}

/** The affiliate's analytics dashboard: totals, per-month series, payout history. */
export async function getAffiliateStats(token: string): Promise<AffiliateStats> {
  const res = await fetch(`${API_BASE_URL}/affiliate/stats`, {
    headers: { "X-Affiliate-Token": token },
  });
  return parse<AffiliateStats>(res);
}

/** The affiliate's referred families, anonymised (age, coarse status, still-earning flag). */
export async function getAffiliateReferrals(token: string): Promise<ReferralRow[]> {
  const res = await fetch(`${API_BASE_URL}/affiliate/referrals`, {
    headers: { "X-Affiliate-Token": token },
  });
  return parse<ReferralRow[]>(res);
}

// ---- admin (uses the logged-in parent's device token) ----------------------

function deviceHeaders(): HeadersInit {
  const deviceToken = localStorage.getItem("deviceToken");
  return deviceToken ? { ...JSON_HEADERS, "X-Device-Token": deviceToken } : { ...JSON_HEADERS };
}

/** Every affiliate with referral counts and payable amounts. Admin-only (config email). */
export async function adminListAffiliates(): Promise<AffiliateAdminRow[]> {
  const res = await fetch(`${API_BASE_URL}/admin/affiliates`, { headers: deviceHeaders() });
  return parse<AffiliateAdminRow[]>(res);
}

/** What a recorded payout covered. */
export type PayoutResult = {
  payoutId: string;
  total: number;
  currency: string;
  commissionCount: number;
};

/** Record a manual payout of everything payable for one affiliate (marks it PAID). */
export async function adminPayout(affiliateId: string, method = "manual"): Promise<PayoutResult> {
  const res = await fetch(`${API_BASE_URL}/admin/affiliates/${affiliateId}/payout`, {
    method: "POST",
    headers: deviceHeaders(),
    body: JSON.stringify({ method }),
  });
  return parse<PayoutResult>(res);
}

/** Invite a new affiliate. Admin-only. */
export async function adminCreateAffiliate(
  name: string,
  email: string,
  referralCode?: string,
): Promise<AffiliateAdminRow> {
  const res = await fetch(`${API_BASE_URL}/admin/affiliates`, {
    method: "POST",
    headers: deviceHeaders(),
    body: JSON.stringify({ name, email, referralCode: referralCode || null }),
  });
  return parse<AffiliateAdminRow>(res);
}
