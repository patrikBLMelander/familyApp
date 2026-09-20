import { useEffect, useState } from "react";
import { KID_QUEST, PublicPage } from "../legal/LegalPage";
import {
  adminCreateAffiliate,
  adminListAffiliates,
  adminPayout,
  type AffiliateAdminRow,
} from "../../shared/api/affiliate";

/**
 * The admin view of the affiliate program at /affiliate/admin.
 *
 * Uses the parent account's device token (already stored by the main app), and the
 * backend gates it to the configured admin email -- so only the owner sees it, even
 * though every parent is technically logged in.
 */
export function AffiliateAdminView() {
  const [rows, setRows] = useState<AffiliateAdminRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    adminListAffiliates()
      .then(setRows)
      .catch((err) => setError(err instanceof Error ? err.message : "Kunde inte hämta affiliates."));
  };

  useEffect(load, []);

  return (
    <PublicPage>
      <h1 style={{ fontSize: "1.5rem", margin: "0 0 0.35rem" }}>Affiliates — admin</h1>
      <p style={{ color: KID_QUEST.textSecondary, margin: "0 0 1.5rem" }}>
        Bjud in affiliates och följ värvningar och utbetalningar.
      </p>

      {error && (
        <p style={{ color: "#B91C1C", margin: "0 0 1.25rem", fontSize: "0.9rem" }}>
          {error} {" "}
          {error.toLowerCase().includes("admin") && "Logga in med ditt adminkonto i appen först."}
        </p>
      )}

      <InviteForm onCreated={load} />

      {rows === null && !error && <p style={{ color: KID_QUEST.muted }}>Laddar…</p>}
      {rows !== null && rows.length === 0 && (
        <p style={{ color: KID_QUEST.muted }}>Inga affiliates ännu — bjud in den första ovan.</p>
      )}
      {rows !== null && rows.length > 0 && <AffiliateTable rows={rows} onPaid={load} />}
    </PublicPage>
  );
}

function InviteForm({ onCreated }: { onCreated: () => void }) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setOk(null);
    try {
      const created = await adminCreateAffiliate(name.trim(), email.trim(), code.trim() || undefined);
      setOk(`Bjöd in ${created.name} (kod ${created.referralCode}).`);
      setName("");
      setEmail("");
      setCode("");
      onCreated();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Kunde inte skapa affiliaten.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <form
      onSubmit={submit}
      style={{
        background: "#fff",
        border: `1px solid ${KID_QUEST.rule}`,
        borderRadius: 14,
        padding: "1.1rem 1.2rem",
        marginBottom: "1.75rem",
        display: "grid",
        gap: "0.75rem",
      }}
    >
      <div style={{ fontWeight: 700 }}>Bjud in en affiliate</div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: "0.6rem" }}>
        <input placeholder="Namn" required value={name} onChange={(e) => setName(e.target.value)} style={inputStyle} />
        <input placeholder="E-post" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} style={inputStyle} />
        <input placeholder="Kod (valfritt)" value={code} onChange={(e) => setCode(e.target.value)} style={inputStyle} />
      </div>
      {error && <p style={{ color: "#B91C1C", margin: 0, fontSize: "0.85rem" }}>{error}</p>}
      {ok && <p style={{ color: "#166534", margin: 0, fontSize: "0.85rem" }}>{ok}</p>}
      <button type="submit" disabled={busy} style={{ ...buttonStyle, opacity: busy ? 0.7 : 1, justifySelf: "start" }}>
        {busy ? "…" : "Bjud in"}
      </button>
    </form>
  );
}

function AffiliateTable({ rows, onPaid }: { rows: AffiliateAdminRow[]; onPaid: () => void }) {
  const totalPayable = rows.reduce((sum, r) => sum + (r.payable ?? 0), 0);
  const [payingId, setPayingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const payOut = async (row: AffiliateAdminRow) => {
    if (row.payable <= 0) return;
    if (!window.confirm(`Markera ${formatKr(row.payable)} till ${row.name} som utbetald?`)) return;
    setPayingId(row.id);
    setError(null);
    try {
      await adminPayout(row.id, "manual");
      onPaid();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Kunde inte registrera utbetalningen.");
    } finally {
      setPayingId(null);
    }
  };

  return (
    <div style={{ overflowX: "auto" }}>
      {error && <p style={{ color: "#B91C1C", margin: "0 0 0.75rem", fontSize: "0.85rem" }}>{error}</p>}
      <table style={{ borderCollapse: "collapse", width: "100%", fontSize: "0.9rem", minWidth: 640 }}>
        <thead>
          <tr style={{ textAlign: "left", color: KID_QUEST.muted, borderBottom: `1px solid ${KID_QUEST.rule}` }}>
            <th style={th}>Namn</th>
            <th style={th}>Kod</th>
            <th style={th}>Status</th>
            <th style={{ ...th, textAlign: "right" }}>Värvade</th>
            <th style={{ ...th, textAlign: "right" }}>Väntande</th>
            <th style={{ ...th, textAlign: "right" }}>Att betala</th>
            <th style={{ ...th, textAlign: "right" }}>Utbetalt</th>
            <th style={th} />
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id} style={{ borderBottom: `1px solid ${KID_QUEST.rule}` }}>
              <td style={td}>
                {r.name}
                <div style={{ color: KID_QUEST.muted, fontSize: "0.8rem" }}>{r.email}</div>
              </td>
              <td style={{ ...td, fontFamily: "monospace" }}>{r.referralCode}</td>
              <td style={td}>{statusLabel(r.status)}</td>
              <td style={{ ...td, textAlign: "right" }}>{r.referralCount}</td>
              <td style={{ ...td, textAlign: "right" }}>{formatKr(r.pending)}</td>
              <td style={{ ...td, textAlign: "right", fontWeight: 700, color: KID_QUEST.accent }}>{formatKr(r.payable)}</td>
              <td style={{ ...td, textAlign: "right" }}>{formatKr(r.paidOut)}</td>
              <td style={{ ...td, textAlign: "right" }}>
                {r.payable > 0 ? (
                  <button onClick={() => payOut(r)} disabled={payingId === r.id} style={payButton(payingId === r.id)}>
                    {payingId === r.id ? "…" : "Markera utbetald"}
                  </button>
                ) : (
                  <span style={{ color: KID_QUEST.muted }}>—</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <td style={{ ...td, fontWeight: 700 }} colSpan={5}>
              Totalt att betala ut
            </td>
            <td style={{ ...td, textAlign: "right", fontWeight: 700, color: KID_QUEST.accent }}>{formatKr(totalPayable)}</td>
            <td style={td} colSpan={2} />
          </tr>
        </tfoot>
      </table>
    </div>
  );
}

function statusLabel(status: string): string {
  switch (status) {
    case "INVITED":
      return "Inbjuden";
    case "ACTIVE":
      return "Aktiv";
    case "PAUSED":
      return "Pausad";
    default:
      return status;
  }
}

function formatKr(value: number): string {
  return `${(value ?? 0).toLocaleString("sv-SE", { minimumFractionDigits: 0, maximumFractionDigits: 2 })} kr`;
}

const inputStyle: React.CSSProperties = {
  padding: "0.6rem 0.7rem",
  borderRadius: 9,
  border: `1px solid ${KID_QUEST.rule}`,
  fontSize: "0.95rem",
  fontFamily: "inherit",
};

const buttonStyle: React.CSSProperties = {
  padding: "0.6rem 1.1rem",
  borderRadius: 10,
  border: "none",
  background: KID_QUEST.accent,
  color: "#fff",
  fontWeight: 700,
  cursor: "pointer",
};

const th: React.CSSProperties = { padding: "0.55rem 0.6rem", fontWeight: 600 };
const td: React.CSSProperties = { padding: "0.6rem 0.6rem", verticalAlign: "top" };

function payButton(busy: boolean): React.CSSProperties {
  return {
    padding: "0.4rem 0.75rem",
    borderRadius: 8,
    border: `1px solid ${KID_QUEST.accent}`,
    background: busy ? KID_QUEST.rule : "transparent",
    color: KID_QUEST.accent,
    fontWeight: 600,
    fontSize: "0.82rem",
    whiteSpace: "nowrap",
    cursor: busy ? "default" : "pointer",
  };
}
