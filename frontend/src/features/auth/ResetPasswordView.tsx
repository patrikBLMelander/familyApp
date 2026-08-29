import { useEffect, useState } from "react";
import { API_BASE_URL } from "../../shared/config";
import { KID_QUEST, PublicPage } from "../legal/LegalPage";

/**
 * Where the emailed reset link lands, at /aterstall-losenord?token=…
 *
 * A web page rather than a deep link into the app, deliberately. The person who needs
 * this is locked out — possibly on a laptop, possibly on a phone that has never had the
 * app installed, possibly the parent whose phone is the thing they lost. A link that
 * only works if you already have the app fails exactly the person it exists for.
 *
 * Public and unauthenticated by necessity: the token in the URL is the only credential
 * the caller has.
 */
export function ResetPasswordView() {
  const [token, setToken] = useState<string | null>(null);
  const [password, setPassword] = useState("");
  const [repeat, setRepeat] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  useEffect(() => {
    const value = new URLSearchParams(window.location.search).get("token");
    setToken(value);
    if (!value) {
      setError("Länken saknar en kod. Kopiera hela adressen från mejlet.");
    }
  }, []);

  const tooShort = password.length > 0 && password.length < 6;
  const mismatch = repeat.length > 0 && password !== repeat;
  const canSubmit = !!token && password.length >= 6 && password === repeat && !saving;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    setSaving(true);
    setError(null);
    try {
      const response = await fetch(`${API_BASE_URL}/families/password-reset/confirm`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token, password }),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(body?.error ?? "Kunde inte spara lösenordet.");
      }
      setDone(true);
    } catch (err) {
      setError(
        err instanceof Error && err.message.includes("invalid or has expired")
          ? "Länken har gått ut eller är redan använd. Begär en ny i appen."
          : err instanceof Error
            ? err.message
            : "Kunde inte spara lösenordet."
      );
    } finally {
      setSaving(false);
    }
  };

  if (done) {
    return (
      <PublicPage>
        <h1 style={{ fontSize: "1.5rem", margin: "0 0 0.5rem" }}>Klart!</h1>
        <p style={{ color: KID_QUEST.textSecondary, margin: 0 }}>
          Ditt lösenord är uppdaterat. Nu kan du logga in i KidQuest med det nya.
        </p>
      </PublicPage>
    );
  }

  return (
    <PublicPage>
      <h1 style={{ fontSize: "1.5rem", margin: "0 0 0.35rem" }}>Välj ett nytt lösenord</h1>
      <p style={{ color: KID_QUEST.textSecondary, margin: "0 0 1.5rem" }}>Minst 6 tecken.</p>

      <form onSubmit={submit} style={{ display: "flex", flexDirection: "column", gap: "1.1rem" }}>
        <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          <span style={{ fontSize: "0.85rem", fontWeight: 600 }}>Nytt lösenord</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            disabled={!token || saving}
            style={inputStyle}
          />
          {tooShort && <small style={{ color: "#991B1B" }}>Minst 6 tecken.</small>}
        </label>

        <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          <span style={{ fontSize: "0.85rem", fontWeight: 600 }}>Upprepa lösenordet</span>
          <input
            type="password"
            value={repeat}
            onChange={(e) => setRepeat(e.target.value)}
            autoComplete="new-password"
            disabled={!token || saving}
            style={inputStyle}
          />
          {mismatch && <small style={{ color: "#991B1B" }}>Lösenorden är inte lika.</small>}
        </label>

        {error && <p style={{ color: "#991B1B", margin: 0, fontSize: "0.95rem" }}>{error}</p>}

        <button
          type="submit"
          disabled={!canSubmit}
          style={{
            padding: "14px 16px",
            borderRadius: 14,
            border: "none",
            background: canSubmit ? KID_QUEST.accent : "#D6D3D1",
            color: "#fff",
            fontSize: "1rem",
            fontWeight: 600,
            cursor: canSubmit ? "pointer" : "default",
          }}
        >
          {saving ? "Sparar…" : "Spara lösenordet"}
        </button>
      </form>
    </PublicPage>
  );
}

const inputStyle: React.CSSProperties = {
  padding: "12px 14px",
  borderRadius: 12,
  border: "1px solid #E7E5E4",
  background: "#fff",
  fontSize: "1rem",
  color: KID_QUEST.textPrimary,
};
