import { useEffect, useState } from "react";
import { KID_QUEST, PublicPage } from "../legal/LegalPage";
import {
  activateAffiliate,
  getAffiliateSelf,
  loginAffiliate,
  type AffiliateSelf,
} from "../../shared/api/affiliate";

const TOKEN_KEY = "affiliateToken";

/**
 * The affiliate portal at /affiliate on the website.
 *
 * A separate audience from families: affiliates are recruited partners, not app users,
 * so this stands outside the family login and carries its own token. Accounts are
 * invite-only -- an admin creates the row first, and activation only sets a password on
 * an invite that already exists.
 */
export function AffiliatePortalView() {
  const [token, setToken] = useState<string | null>(() => {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  });

  const saveToken = (value: string) => {
    try {
      localStorage.setItem(TOKEN_KEY, value);
    } catch {
      /* private window: keep it in memory for this session */
    }
    setToken(value);
  };

  const logout = () => {
    try {
      localStorage.removeItem(TOKEN_KEY);
    } catch {
      /* ignore */
    }
    setToken(null);
  };

  return (
    <PublicPage>
      {token ? <Dashboard token={token} onLogout={logout} /> : <AuthForm onSession={saveToken} />}
    </PublicPage>
  );
}

// ---- auth (login / activate) -----------------------------------------------

function AuthForm({ onSession }: { onSession: (token: string) => void }) {
  const [mode, setMode] = useState<"login" | "activate">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const session =
        mode === "login"
          ? await loginAffiliate(email.trim(), password)
          : await activateAffiliate(email.trim(), password);
      onSession(session.token);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Något gick fel.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <h1 style={{ fontSize: "1.5rem", margin: "0 0 0.35rem" }}>Affiliate-portal</h1>
      <p style={{ color: KID_QUEST.textSecondary, margin: "0 0 1.5rem" }}>
        {mode === "login"
          ? "Logga in för att se dina värvningar och intjäning."
          : "Aktivera ditt konto genom att välja ett lösenord. Fungerar bara om du blivit inbjuden."}
      </p>

      <div style={{ display: "flex", gap: "0.5rem", marginBottom: "1.25rem" }}>
        <TabButton active={mode === "login"} onClick={() => setMode("login")}>
          Logga in
        </TabButton>
        <TabButton active={mode === "activate"} onClick={() => setMode("activate")}>
          Aktivera konto
        </TabButton>
      </div>

      <form onSubmit={submit} style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
        <Field label="E-post">
          <input
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            style={inputStyle}
            autoComplete="email"
          />
        </Field>
        <Field label="Lösenord">
          <input
            type="password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            style={inputStyle}
            autoComplete={mode === "login" ? "current-password" : "new-password"}
          />
        </Field>
        {error && <p style={{ color: "#B91C1C", margin: 0, fontSize: "0.9rem" }}>{error}</p>}
        <button type="submit" disabled={busy} style={primaryButton(busy)}>
          {busy ? "…" : mode === "login" ? "Logga in" : "Aktivera"}
        </button>
      </form>
    </div>
  );
}

// ---- dashboard --------------------------------------------------------------

function Dashboard({ token, onLogout }: { token: string; onLogout: () => void }) {
  const [data, setData] = useState<AffiliateSelf | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getAffiliateSelf(token)
      .then((d) => !cancelled && setData(d))
      .catch((err) => {
        if (cancelled) return;
        // A stale or invalid token: send them back to the login form.
        setError(err instanceof Error ? err.message : "Kunde inte hämta din data.");
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  if (error) {
    return (
      <div>
        <p style={{ color: KID_QUEST.textSecondary, margin: "0 0 1rem" }}>{error}</p>
        <button onClick={onLogout} style={primaryButton(false)}>
          Till inloggningen
        </button>
      </div>
    );
  }

  if (!data) {
    return <p style={{ color: KID_QUEST.muted, margin: 0 }}>Laddar…</p>;
  }

  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(data.referralLink);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      /* clipboard blocked; the link is visible to copy by hand */
    }
  };

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
        <h1 style={{ fontSize: "1.5rem", margin: "0 0 0.25rem" }}>Hej {data.name}!</h1>
        <button onClick={onLogout} style={linkButton}>
          Logga ut
        </button>
      </div>
      <p style={{ color: KID_QUEST.textSecondary, margin: "0 0 1.5rem" }}>
        Din provision: {formatPct(data.commissionPct)} av varje betald månad.
      </p>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))", gap: "0.75rem", marginBottom: "1.5rem" }}>
        <Stat label="Värvade familjer" value={String(data.referralCount)} />
        <Stat label="Väntande" value={formatKr(data.pending)} />
        <Stat label="Att betala ut" value={formatKr(data.approved)} accent />
        <Stat label="Utbetalt" value={formatKr(data.paidOut)} />
      </div>

      <div style={cardStyle}>
        <div style={{ fontSize: "0.8rem", color: KID_QUEST.muted, marginBottom: "0.35rem" }}>DIN KOD</div>
        <div style={{ fontSize: "1.6rem", fontWeight: 700, letterSpacing: "0.05em", color: KID_QUEST.accent }}>
          {data.referralCode}
        </div>
        <div style={{ marginTop: "0.9rem", fontSize: "0.8rem", color: KID_QUEST.muted }}>DIN LÄNK</div>
        <div style={{ display: "flex", gap: "0.5rem", alignItems: "center", marginTop: "0.35rem", flexWrap: "wrap" }}>
          <code style={{ fontSize: "0.9rem", wordBreak: "break-all", flex: 1 }}>{data.referralLink}</code>
          <button onClick={copyLink} style={smallButton}>
            {copied ? "Kopierad!" : "Kopiera"}
          </button>
        </div>
      </div>
    </div>
  );
}

// ---- little building blocks -------------------------------------------------

function Stat({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
  return (
    <div style={cardStyle}>
      <div style={{ fontSize: "0.78rem", color: KID_QUEST.muted, marginBottom: "0.3rem" }}>{label}</div>
      <div style={{ fontSize: "1.35rem", fontWeight: 700, color: accent ? KID_QUEST.accent : KID_QUEST.textPrimary }}>
        {value}
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: "flex", flexDirection: "column", gap: "0.35rem", fontSize: "0.9rem", color: KID_QUEST.textSecondary }}>
      {label}
      {children}
    </label>
  );
}

function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        flex: 1,
        padding: "0.55rem",
        borderRadius: 10,
        border: `1px solid ${active ? KID_QUEST.accent : KID_QUEST.rule}`,
        background: active ? KID_QUEST.accent : "transparent",
        color: active ? "#fff" : KID_QUEST.textSecondary,
        fontWeight: 600,
        cursor: "pointer",
      }}
    >
      {children}
    </button>
  );
}

const inputStyle: React.CSSProperties = {
  padding: "0.65rem 0.75rem",
  borderRadius: 10,
  border: `1px solid ${KID_QUEST.rule}`,
  fontSize: "1rem",
  fontFamily: "inherit",
};

const cardStyle: React.CSSProperties = {
  background: "#fff",
  border: `1px solid ${KID_QUEST.rule}`,
  borderRadius: 14,
  padding: "1rem 1.1rem",
};

const smallButton: React.CSSProperties = {
  padding: "0.45rem 0.85rem",
  borderRadius: 9,
  border: "none",
  background: KID_QUEST.accent,
  color: "#fff",
  fontWeight: 600,
  cursor: "pointer",
};

const linkButton: React.CSSProperties = {
  border: "none",
  background: "transparent",
  color: KID_QUEST.accent,
  cursor: "pointer",
  fontSize: "0.9rem",
  fontWeight: 600,
};

function primaryButton(busy: boolean): React.CSSProperties {
  return {
    padding: "0.7rem 1rem",
    borderRadius: 11,
    border: "none",
    background: KID_QUEST.accent,
    color: "#fff",
    fontWeight: 700,
    fontSize: "1rem",
    cursor: busy ? "default" : "pointer",
    opacity: busy ? 0.7 : 1,
  };
}

function formatKr(value: number): string {
  return `${(value ?? 0).toLocaleString("sv-SE", { minimumFractionDigits: 0, maximumFractionDigits: 2 })} kr`;
}

function formatPct(value: number): string {
  return `${(value ?? 0).toLocaleString("sv-SE", { maximumFractionDigits: 2 })} %`;
}
