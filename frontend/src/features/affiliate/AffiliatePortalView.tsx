import { useEffect, useState } from "react";
import {
  activateAffiliate,
  getAffiliateReferrals,
  getAffiliateStats,
  loginAffiliate,
  type AffiliateStats,
  type MonthPoint,
  type PayoutPoint,
  type ReferralRow,
} from "../../shared/api/affiliate";

const TOKEN_KEY = "affiliateToken";

/**
 * The affiliate portal at /affiliate: invite-only login/activation, then an analytics
 * dashboard (earnings over time, what's payable, total earned). Its own visual identity,
 * deliberately not the KidQuest family theme -- this is a partner-facing tool.
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
      /* private window: memory only */
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
    <div className="affp">
      <style>{CSS}</style>
      <div className="affp-wrap">
        {token ? <Dashboard token={token} onLogout={logout} /> : <AuthForm onSession={saveToken} />}
      </div>
    </div>
  );
}

// ---- auth -------------------------------------------------------------------

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
    <div className="affp-authcard">
      <div className="affp-logo">KidQuest · Affiliates</div>
      <h1 className="affp-h1">{mode === "login" ? "Logga in" : "Aktivera konto"}</h1>
      <p className="affp-sub">
        {mode === "login"
          ? "Se dina värvningar och din intjäning."
          : "Välj ett lösenord. Fungerar bara om du blivit inbjuden."}
      </p>
      <div className="affp-tabs">
        <button className={`affp-tab ${mode === "login" ? "on" : ""}`} onClick={() => setMode("login")} type="button">
          Logga in
        </button>
        <button className={`affp-tab ${mode === "activate" ? "on" : ""}`} onClick={() => setMode("activate")} type="button">
          Aktivera konto
        </button>
      </div>
      <form onSubmit={submit} className="affp-form">
        <label className="affp-field">
          E-post
          <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
        </label>
        <label className="affp-field">
          Lösenord
          <input
            type="password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete={mode === "login" ? "current-password" : "new-password"}
          />
        </label>
        {error && <p className="affp-err">{error}</p>}
        <button type="submit" disabled={busy} className="affp-primary">
          {busy ? "…" : mode === "login" ? "Logga in" : "Aktivera"}
        </button>
      </form>
    </div>
  );
}

// ---- dashboard --------------------------------------------------------------

function Dashboard({ token, onLogout }: { token: string; onLogout: () => void }) {
  const [data, setData] = useState<AffiliateStats | null>(null);
  const [referrals, setReferrals] = useState<ReferralRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    let cancelled = false;
    getAffiliateStats(token)
      .then((d) => !cancelled && setData(d))
      .catch((err) => !cancelled && setError(err instanceof Error ? err.message : "Kunde inte hämta din data."));
    getAffiliateReferrals(token)
      .then((r) => !cancelled && setReferrals(r))
      .catch(() => !cancelled && setReferrals([]));
    return () => {
      cancelled = true;
    };
  }, [token]);

  if (error) {
    return (
      <div className="affp-authcard">
        <p className="affp-sub">{error}</p>
        <button className="affp-primary" onClick={onLogout}>
          Till inloggningen
        </button>
      </div>
    );
  }
  if (!data) {
    return <p className="affp-loading">Laddar…</p>;
  }

  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(data.referralLink);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      /* clipboard blocked */
    }
  };

  const cumulative = data.monthly.reduce<number[]>((acc, m) => {
    acc.push((acc.length ? acc[acc.length - 1] : 0) + m.earned);
    return acc;
  }, []);

  return (
    <>
      <header className="affp-header">
        <div>
          <h1 className="affp-hi">Hej {data.name.split(" ")[0]} 👋</h1>
          <p className="affp-provision">
            Du tjänar <b>{formatPct(data.commissionPct)}</b> av varje betald månad, i upp till 12 månader per värvad familj.
          </p>
        </div>
        <div className="affp-codechip">
          <div>
            <div className="affp-chiplab">Din kod</div>
            <div className="affp-chipval">{data.referralCode}</div>
          </div>
          <button className="affp-copy" onClick={copyLink}>
            {copied ? "Kopierad!" : "Kopiera länk"}
          </button>
        </div>
      </header>

      <div className="affp-kpis">
        <Kpi label="Totalt intjänat" value={kr(data.totalEarned)} dot="money" valueClass="money" />
        <Kpi label="Att betala ut" value={kr(data.payable)} dot="amber" valueClass="amber" hint="redo att betalas" />
        <Kpi label="Väntande (karens)" value={kr(data.pending)} dot="slate" hint="mognar inom 30 dgr" />
        <Kpi label="Denna månad" value={kr(data.thisMonthEarned)} dot="money" hint={`${data.referralCount} värvade totalt`} />
      </div>

      <div className="affp-grid2">
        <div className="affp-card">
          <h2>Intjäning över tid</h2>
          <p className="affp-cap">Ackumulerat, kr</p>
          <AreaChart cumulative={cumulative} months={data.monthly} />
        </div>
        <div className="affp-card">
          <h2>Var pengarna är</h2>
          <p className="affp-cap">Fördelning av allt du tjänat</p>
          <SplitBar paid={data.paidOut} payable={data.payable} pending={data.pending} />
        </div>
      </div>

      <div className="affp-grid2">
        <div className="affp-card">
          <h2>Per månad</h2>
          <p className="affp-cap">Intjänat per månad, kr</p>
          <BarChart months={data.monthly} />
        </div>
        <div className="affp-card">
          <div className="affp-cardhead">
            <h2>Utbetalningar</h2>
            <button className="affp-linkbtn" onClick={onLogout}>
              Logga ut
            </button>
          </div>
          <p className="affp-cap">Historik</p>
          <Payouts payouts={data.payouts} />
        </div>
      </div>

      <div className="affp-card">
        <h2>Dina värvningar</h2>
        <p className="affp-cap">Anonymt — hur länge de varit med och om de fortfarande ger provision.</p>
        <Referrals referrals={referrals} />
      </div>
    </>
  );
}

function Referrals({ referrals }: { referrals: ReferralRow[] | null }) {
  const [showAll, setShowAll] = useState(false);
  if (referrals === null) {
    return <p className="affp-empty">Laddar…</p>;
  }
  if (referrals.length === 0) {
    return <p className="affp-empty">Inga värvningar ännu. Dela din länk för att komma igång!</p>;
  }
  const paying = referrals.filter((r) => r.status === "PAYING").length;
  const trial = referrals.filter((r) => r.status === "TRIAL").length;
  const ended = referrals.filter((r) => r.status === "ENDED").length;
  const shown = showAll ? referrals : referrals.slice(0, 10);
  return (
    <>
      <div className="affp-refsummary">
        <span><b>{paying}</b> betalar</span>
        <span><b>{trial}</b> provgratis</span>
        <span><b>{ended}</b> avslutade</span>
      </div>
      <div className="affp-reflist">
        {shown.map((r, i) => (
          <div className="affp-refrow" key={i}>
            <span className="affp-refage">{ageLabel(r.joinedMonthsAgo)}</span>
            <span className={`affp-refbadge ${refClass(r.status)}`}>{statusLabel(r.status)}</span>
            <span className="affp-refprov">
              {r.earningCommission ? (
                <><span className="affp-dotc" style={{ background: "var(--affp-money)" }} /> ger provision</>
              ) : (
                <span className="affp-refmuted">—</span>
              )}
            </span>
          </div>
        ))}
      </div>
      {referrals.length > 10 && (
        <button className="affp-linkbtn" style={{ marginTop: 10 }} onClick={() => setShowAll((v) => !v)}>
          {showAll ? "Visa färre" : `Visa alla (${referrals.length})`}
        </button>
      )}
    </>
  );
}

function Kpi({
  label,
  value,
  dot,
  valueClass,
  hint,
}: {
  label: string;
  value: string;
  dot: string;
  valueClass?: string;
  hint?: string;
}) {
  return (
    <div className="affp-kpi">
      <div className="affp-kpil">
        <span className={`affp-dot ${dot}`} />
        {label}
      </div>
      <div className={`affp-kpiv ${valueClass ?? ""}`}>{value}</div>
      {hint && <div className="affp-kpid">{hint}</div>}
    </div>
  );
}

// ---- charts (inline SVG) ----------------------------------------------------

function AreaChart({ cumulative, months }: { cumulative: number[]; months: MonthPoint[] }) {
  const W = 640, H = 220, L = 46, R = 12, T = 16, B = 28;
  const total = cumulative.length ? cumulative[cumulative.length - 1] : 0;
  const max = Math.max(500, Math.ceil(total / 500) * 500);
  const n = Math.max(months.length, 2);
  const x = (i: number) => L + ((W - L - R) * i) / (n - 1);
  const y = (v: number) => T + (H - T - B) * (1 - v / max);
  const line = cumulative.map((v, i) => `${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(" ");
  const area = `${L},${y(0)} ${line} ${x(cumulative.length - 1)},${y(0)}`;
  const last = cumulative.length - 1;
  const ticks = [0, max / 2, max];
  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="affp-svg" preserveAspectRatio="none">
      <defs>
        <linearGradient id="affp-ag" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="var(--affp-money)" stopOpacity="0.28" />
          <stop offset="1" stopColor="var(--affp-money)" stopOpacity="0" />
        </linearGradient>
      </defs>
      {ticks.map((t) => (
        <g key={t}>
          <line className="affp-gridline" x1={L} y1={y(t)} x2={W - R} y2={y(t)} />
          <text className="affp-axis" x={L - 8} y={y(t) + 4} textAnchor="end">
            {t.toLocaleString("sv-SE")}
          </text>
        </g>
      ))}
      {cumulative.length > 1 && <polygon points={area} fill="url(#affp-ag)" />}
      <polyline points={line} fill="none" stroke="var(--affp-money)" strokeWidth="2.5" strokeLinejoin="round" strokeLinecap="round" />
      {last >= 0 && (
        <>
          <circle cx={x(last)} cy={y(cumulative[last])} r="9" fill="var(--affp-money)" opacity="0.18" />
          <circle cx={x(last)} cy={y(cumulative[last])} r="4.5" fill="var(--affp-money)" />
        </>
      )}
      {months.map((m, i) => (
        <text key={m.month} className="affp-axis" x={x(i)} y={H - 8} textAnchor="middle">
          {shortMonth(m.month)}
        </text>
      ))}
    </svg>
  );
}

function BarChart({ months }: { months: MonthPoint[] }) {
  const W = 640, H = 200, L = 46, R = 12, T = 14, B = 28;
  const vals = months.map((m) => m.earned);
  const max = Math.max(100, Math.ceil(Math.max(...vals, 0) / 100) * 100);
  const n = Math.max(months.length, 1);
  const cx = (i: number) => L + (W - L - R) * ((i + 0.5) / n);
  const bw = ((W - L - R) / n) * 0.55;
  const y = (v: number) => T + (H - T - B) * (1 - v / max);
  const ticks = [0, max / 2, max];
  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="affp-svg">
      {ticks.map((t) => (
        <g key={t}>
          <line className="affp-gridline" x1={L} y1={y(t)} x2={W - R} y2={y(t)} />
          <text className="affp-axis" x={L - 8} y={y(t) + 4} textAnchor="end">
            {t}
          </text>
        </g>
      ))}
      {months.map((m, i) => {
        const isLast = i === months.length - 1;
        const h = (H - T - B) * (m.earned / max);
        return (
          <rect
            key={m.month}
            x={cx(i) - bw / 2}
            y={y(m.earned)}
            width={bw}
            height={Math.max(0, h)}
            rx="4"
            fill={isLast ? "var(--affp-money)" : "var(--affp-money-soft)"}
            stroke={isLast ? "none" : "var(--affp-money)"}
            strokeOpacity="0.5"
          />
        );
      })}
      {months.map((m, i) => (
        <text key={m.month} className="affp-axis" x={cx(i)} y={H - 8} textAnchor="middle">
          {shortMonth(m.month)}
        </text>
      ))}
    </svg>
  );
}

function SplitBar({ paid, payable, pending }: { paid: number; payable: number; pending: number }) {
  const parts = [
    { k: "Utbetalt", v: paid, c: "var(--affp-money)" },
    { k: "Att betala ut", v: payable, c: "var(--affp-amber)" },
    { k: "Väntande (karens)", v: pending, c: "var(--affp-slate)" },
  ];
  const sum = Math.max(1, parts.reduce((a, p) => a + p.v, 0));
  return (
    <>
      <div className="affp-splitbar">
        {parts.map((p) => (
          <div key={p.k} style={{ width: `${(100 * p.v) / sum}%`, background: p.c }} />
        ))}
      </div>
      <div className="affp-legend">
        {parts.map((p) => (
          <div className="affp-legrow" key={p.k}>
            <span className="affp-lft">
              <span className="affp-dotc" style={{ background: p.c }} />
              {p.k}
            </span>
            <span className="affp-amt">{kr(p.v)}</span>
          </div>
        ))}
      </div>
    </>
  );
}

function Payouts({ payouts }: { payouts: PayoutPoint[] }) {
  if (payouts.length === 0) {
    return <p className="affp-empty">Inga utbetalningar ännu.</p>;
  }
  return (
    <div className="affp-payouts">
      {payouts.map((p, i) => (
        <div className="affp-prow" key={i}>
          <div>
            <div className="affp-when">{formatDate(p.paidAt)}</div>
            <div className="affp-method">{p.method}</div>
          </div>
          <div className="affp-prowr">
            <span className="affp-tag">Betald</span>
            <span className="affp-amt">{kr(p.amount)}</span>
          </div>
        </div>
      ))}
    </div>
  );
}

// ---- helpers ----------------------------------------------------------------

function kr(v: number): string {
  return `${(v ?? 0).toLocaleString("sv-SE", { maximumFractionDigits: 2 })} kr`;
}
function formatPct(v: number): string {
  return `${(v ?? 0).toLocaleString("sv-SE", { maximumFractionDigits: 2 })} %`;
}
const SV_MONTHS = ["jan", "feb", "mar", "apr", "maj", "jun", "jul", "aug", "sep", "okt", "nov", "dec"];
function shortMonth(ym: string): string {
  const m = parseInt(ym.slice(5, 7), 10);
  return SV_MONTHS[m - 1] ?? ym;
}
function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString("sv-SE", { day: "numeric", month: "short", year: "numeric" });
  } catch {
    return iso;
  }
}
function ageLabel(monthsAgo: number): string {
  if (monthsAgo <= 0) return "Ny denna månad";
  if (monthsAgo === 1) return "1 månad";
  return `${monthsAgo} månader`;
}
function statusLabel(status: string): string {
  switch (status) {
    case "PAYING": return "Betalar";
    case "TRIAL": return "Provgratis";
    case "ENDED": return "Avslutad";
    default: return "Okänd";
  }
}
function refClass(status: string): string {
  switch (status) {
    case "PAYING": return "paying";
    case "TRIAL": return "trial";
    case "ENDED": return "ended";
    default: return "unknown";
  }
}

const CSS = `
.affp{
  --bg:#f4f6f9; --panel:#fff; --panel2:#f8fafc; --ink:#0f1729; --ink2:#5a6478; --muted:#8a93a6;
  --line:#e7ebf1; --line2:#eef2f7; --affp-money:#0e9f6e; --affp-money-soft:#d8f3e7;
  --affp-amber:#d98a0b; --affp-slate:#5b6b8c; --accent:#3b6fe0;
  --shadow:0 1px 2px rgba(16,23,41,.04),0 8px 24px rgba(16,23,41,.06);
  min-height:100vh; background:var(--bg); color:var(--ink);
  font-family:"Manrope",system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;
}
@media (prefers-color-scheme:dark){ .affp{
  --bg:#0c1018; --panel:#141a24; --panel2:#1a212d; --ink:#eaf0f8; --ink2:#a3adc0; --muted:#7a869c;
  --line:#242c39; --line2:#1e2532; --affp-money:#34d399; --affp-money-soft:#123026;
  --affp-amber:#f2b34d; --affp-slate:#93a2c2; --accent:#6b9bff;
  --shadow:0 1px 2px rgba(0,0,0,.3),0 10px 28px rgba(0,0,0,.4);
}}
.affp-wrap{max-width:1000px;margin:0 auto;padding:28px 18px 72px;}
.affp *{box-sizing:border-box}
.affp-header{display:flex;justify-content:space-between;align-items:flex-start;gap:16px;flex-wrap:wrap;margin-bottom:22px;}
.affp-hi{font-size:1.45rem;font-weight:800;letter-spacing:-.02em;margin:0;}
.affp-provision{color:var(--ink2);font-size:.9rem;margin:.2rem 0 0;}
.affp-codechip{display:flex;align-items:center;gap:10px;background:var(--panel);border:1px solid var(--line);border-radius:12px;padding:8px 10px 8px 14px;box-shadow:var(--shadow);}
.affp-chiplab{font-size:.68rem;letter-spacing:.12em;text-transform:uppercase;color:var(--muted);}
.affp-chipval{font-weight:700;letter-spacing:.06em;color:var(--accent);}
.affp-copy{border:none;background:var(--panel2);color:var(--ink2);border-radius:8px;padding:6px 10px;font-size:.8rem;font-weight:600;cursor:pointer;font-family:inherit;}
.affp-kpis{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-bottom:16px;}
.affp-kpi{background:var(--panel);border:1px solid var(--line);border-radius:16px;padding:16px;box-shadow:var(--shadow);}
.affp-kpil{font-size:.76rem;color:var(--muted);font-weight:600;margin-bottom:8px;display:flex;align-items:center;gap:6px;}
.affp-kpiv{font-size:1.7rem;font-weight:800;letter-spacing:-.02em;line-height:1;font-variant-numeric:tabular-nums;}
.affp-kpiv.money{color:var(--affp-money);} .affp-kpiv.amber{color:var(--affp-amber);}
.affp-kpid{font-size:.76rem;margin-top:7px;font-weight:600;color:var(--muted);}
.affp-dot{width:8px;height:8px;border-radius:50%;display:inline-block;}
.affp-dot.money{background:var(--affp-money);} .affp-dot.amber{background:var(--affp-amber);} .affp-dot.slate{background:var(--affp-slate);}
.affp-grid2{display:grid;grid-template-columns:1.55fr 1fr;gap:14px;margin-bottom:14px;}
.affp-card{background:var(--panel);border:1px solid var(--line);border-radius:16px;padding:18px 18px 14px;box-shadow:var(--shadow);}
.affp-cardhead{display:flex;justify-content:space-between;align-items:baseline;}
.affp-card h2{font-size:.95rem;font-weight:700;margin:0 0 2px;}
.affp-cap{font-size:.78rem;color:var(--muted);margin:0 0 12px;}
.affp-svg{display:block;width:100%;height:auto;overflow:visible;}
.affp-axis{fill:var(--muted);font-size:11px;font-family:"IBM Plex Mono",monospace;}
.affp-gridline{stroke:var(--line2);stroke-width:1;}
.affp-splitbar{display:flex;height:14px;border-radius:8px;overflow:hidden;margin:4px 0 14px;}
.affp-legend{display:flex;flex-direction:column;gap:12px;}
.affp-legrow{display:flex;align-items:center;justify-content:space-between;gap:10px;font-size:.9rem;}
.affp-lft{display:flex;align-items:center;gap:9px;color:var(--ink2);}
.affp-dotc{width:10px;height:10px;border-radius:3px;display:inline-block;}
.affp-amt{font-weight:700;font-variant-numeric:tabular-nums;}
.affp-payouts .affp-prow{display:flex;justify-content:space-between;align-items:center;padding:11px 2px;border-bottom:1px solid var(--line2);}
.affp-payouts .affp-prow:last-child{border-bottom:none;}
.affp-when{font-size:.9rem;font-weight:600;} .affp-method{font-size:.78rem;color:var(--muted);}
.affp-prowr{display:flex;align-items:center;gap:10px;}
.affp-tag{font-size:.72rem;padding:2px 8px;border-radius:999px;background:var(--affp-money-soft);color:var(--affp-money);font-weight:700;}
.affp-linkbtn{border:none;background:transparent;color:var(--accent);font-weight:600;font-size:.85rem;cursor:pointer;}
.affp-empty{color:var(--muted);font-size:.9rem;}
.affp-loading{color:var(--muted);text-align:center;padding:60px 0;}
.affp-refsummary{display:flex;gap:18px;flex-wrap:wrap;font-size:.9rem;color:var(--ink2);margin-bottom:14px;}
.affp-refsummary b{color:var(--ink);}
.affp-reflist{display:flex;flex-direction:column;}
.affp-refrow{display:grid;grid-template-columns:1fr auto auto;gap:12px;align-items:center;padding:10px 2px;border-bottom:1px solid var(--line2);font-size:.9rem;}
.affp-refrow:last-child{border-bottom:none;}
.affp-refage{color:var(--ink2);}
.affp-refbadge{font-size:.74rem;font-weight:700;padding:2px 9px;border-radius:999px;white-space:nowrap;}
.affp-refbadge.paying{background:var(--affp-money-soft);color:var(--affp-money);}
.affp-refbadge.trial{background:var(--line2);color:var(--ink2);}
.affp-refbadge.ended{background:var(--line2);color:var(--muted);}
.affp-refbadge.unknown{background:var(--line2);color:var(--muted);}
.affp-refprov{display:flex;align-items:center;gap:7px;font-size:.8rem;color:var(--ink2);justify-content:flex-end;min-width:110px;}
.affp-refmuted{color:var(--muted);}
.affp-authcard{max-width:400px;margin:8vh auto 0;background:var(--panel);border:1px solid var(--line);border-radius:18px;padding:28px 26px;box-shadow:var(--shadow);}
.affp-logo{font-size:.8rem;font-weight:700;letter-spacing:.04em;color:var(--accent);margin-bottom:14px;}
.affp-h1{font-size:1.4rem;font-weight:800;margin:0 0 .3rem;}
.affp-sub{color:var(--ink2);font-size:.9rem;margin:0 0 1.3rem;line-height:1.5;}
.affp-tabs{display:flex;gap:8px;margin-bottom:1.1rem;}
.affp-tab{flex:1;padding:.55rem;border-radius:10px;border:1px solid var(--line);background:transparent;color:var(--ink2);font-weight:600;cursor:pointer;font-family:inherit;}
.affp-tab.on{background:var(--accent);border-color:var(--accent);color:#fff;}
.affp-form{display:flex;flex-direction:column;gap:1rem;}
.affp-field{display:flex;flex-direction:column;gap:.35rem;font-size:.85rem;color:var(--ink2);}
.affp-field input{padding:.65rem .75rem;border-radius:10px;border:1px solid var(--line);font-size:1rem;font-family:inherit;background:var(--panel2);color:var(--ink);}
.affp-err{color:#dc2626;font-size:.85rem;margin:0;}
.affp-primary{padding:.7rem 1rem;border-radius:11px;border:none;background:var(--accent);color:#fff;font-weight:700;font-size:1rem;cursor:pointer;font-family:inherit;}
@media (max-width:760px){ .affp-kpis{grid-template-columns:repeat(2,1fr);} .affp-grid2{grid-template-columns:1fr;} }
`;
