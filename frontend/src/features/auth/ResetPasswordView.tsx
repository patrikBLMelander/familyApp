import { useEffect, useState } from "react";
import { API_BASE_URL } from "../../shared/config";

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

  const wrapper: React.CSSProperties = {
    maxWidth: 420,
    margin: "0 auto",
    padding: "3rem 1.5rem",
    lineHeight: 1.6,
    color: "#1C1917",
    fontFamily: "system-ui, -apple-system, sans-serif",
  };

  if (done) {
    return (
      <div style={wrapper}>
        <h1 style={{ fontSize: "1.5rem", marginBottom: "0.5rem" }}>Klart!</h1>
        <p>Ditt lösenord är uppdaterat. Nu kan du logga in i KidQuest med det nya.</p>
      </div>
    );
  }

  return (
    <div style={wrapper}>
      <h1 style={{ fontSize: "1.5rem", marginBottom: "0.5rem" }}>Välj ett nytt lösenord</h1>
      <p style={{ color: "#57534E" }}>Minst 6 tecken.</p>

      <form onSubmit={submit} style={{ display: "flex", flexDirection: "column", gap: "1rem", marginTop: "1.5rem" }}>
        <label style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          <span style={{ fontSize: "0.9rem", fontWeight: 600 }}>Nytt lösenord</span>
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
          <span style={{ fontSize: "0.9rem", fontWeight: 600 }}>Upprepa lösenordet</span>
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

        {error && <p style={{ color: "#991B1B", margin: 0 }}>{error}</p>}

        <button
          type="submit"
          disabled={!canSubmit}
          style={{
            padding: "12px 16px",
            borderRadius: 12,
            border: "none",
            background: canSubmit ? "#0C4A6E" : "#CBD5E1",
            color: "#fff",
            fontSize: "1rem",
            fontWeight: 600,
            cursor: canSubmit ? "pointer" : "default",
          }}
        >
          {saving ? "Sparar…" : "Spara lösenordet"}
        </button>
      </form>
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  padding: "10px 12px",
  borderRadius: 10,
  border: "1px solid #D6D3D1",
  fontSize: "1rem",
};
