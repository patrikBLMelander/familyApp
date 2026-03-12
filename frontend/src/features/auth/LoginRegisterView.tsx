import { useState } from "react";
import { registerFamily, loginByEmail } from "../../shared/api/family";
import { getMemberByDeviceToken } from "../../shared/api/familyMembers";

type Screen = "welcome" | "child-info" | "parent" | "token";

type LoginRegisterViewProps = {
  onLogin: (deviceToken: string) => void;
};

export function LoginRegisterView({ onLogin }: LoginRegisterViewProps) {
  const [screen, setScreen] = useState<Screen>("welcome");
  const [isRegistering, setIsRegistering] = useState(false);
  const [familyName, setFamilyName] = useState("");
  const [adminName, setAdminName] = useState("");
  const [adminEmail, setAdminEmail] = useState("");
  const [deviceToken, setDeviceToken] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [registerPassword, setRegisterPassword] = useState("");
  const [showRegisterPassword, setShowRegisterPassword] = useState(false);
  const [registerPasswordConfirm, setRegisterPasswordConfirm] = useState("");
  const [showRegisterPasswordConfirm, setShowRegisterPasswordConfirm] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    if (registerPassword.length < 6) {
      setError("Lösenordet måste vara minst 6 tecken långt.");
      setLoading(false);
      return;
    }
    if (registerPassword !== registerPasswordConfirm) {
      setError("Lösenorden matchar inte.");
      setLoading(false);
      return;
    }

    try {
      const result = await registerFamily(familyName, adminName, adminEmail, registerPassword);
      localStorage.setItem("deviceToken", result.deviceToken);
      onLogin(result.deviceToken);
    } catch {
      setError("Kunde inte registrera familj. Försök igen.");
    } finally {
      setLoading(false);
    }
  };

  const handleEmailLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    if (!password) {
      setError("Lösenord krävs för inloggning med e-post.");
      setLoading(false);
      return;
    }

    try {
      const result = await loginByEmail(email, password);
      localStorage.setItem("deviceToken", result.deviceToken);
      onLogin(result.deviceToken);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      if (msg.includes("password") || msg.includes("Password")) {
        setError("Felaktigt lösenord. Försök igen.");
      } else if (msg.includes("not set") || msg.includes("Please set a password")) {
        setError("Lösenord är inte satt för detta konto. Logga in med din inbjudningskod.");
      } else {
        setError("Felaktig e-postadress eller lösenord.");
      }
    } finally {
      setLoading(false);
    }
  };

  const handleTokenLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      await getMemberByDeviceToken(deviceToken);
      localStorage.setItem("deviceToken", deviceToken);
      onLogin(deviceToken);
    } catch {
      setError("Ogiltig inbjudningskod. Kontrollera att koden är korrekt.");
    } finally {
      setLoading(false);
    }
  };

  const goBack = () => {
    setError(null);
    setScreen("welcome");
  };

  // ─── Welcome screen ─────────────────────────────────────────────────────────
  if (screen === "welcome") {
    return (
      <div className="welcome-screen">
        <div className="welcome-hero">
          <img
            src="/onboarding_hero_family.png"
            alt="Familj som gör sysslor"
            className="welcome-hero-img"
            onError={(e) => { (e.target as HTMLImageElement).style.display = "none"; }}
          />
        </div>

        <div className="welcome-content">
          <h1 className="welcome-headline">
            Gör tråkiga sysslor till<br />roliga uppdrag
          </h1>
          <p className="welcome-subheadline">
            Varje månad ett hemligt ägg. Barnen matar djuret med vardagsuppdrag och levlar upp — plus belöningar som motiverar.
          </p>

          <div className="welcome-cards">
            <div className="welcome-card">
              <img src="/onboarding_card_pet_xp.png" alt="" className="welcome-card-img"
                onError={(e) => { (e.target as HTMLImageElement).style.display = "none"; }} />
              <p className="welcome-card-title">Hemligt ägg varje månad</p>
              <p className="welcome-card-desc">Barnen får ett nytt ägg — en överraskning vilket djur det blir.</p>
            </div>
            <div className="welcome-card">
              <img src="/onboarding_card_family_overview.png" alt="" className="welcome-card-img"
                onError={(e) => { (e.target as HTMLImageElement).style.display = "none"; }} />
              <p className="welcome-card-title">Uppdrag matar djuret</p>
              <p className="welcome-card-desc">Vardagsuppdrag ger XP som matar och levlar upp djuret.</p>
            </div>
            <div className="welcome-card">
              <img src="/onboarding_card_rewards_savings.png" alt="" className="welcome-card-img"
                onError={(e) => { (e.target as HTMLImageElement).style.display = "none"; }} />
              <p className="welcome-card-title">Belöningar som motiverar</p>
              <p className="welcome-card-desc">Koppla uppdrag till veckopeng eller små mål — om du vill.</p>
            </div>
          </div>

          <div className="welcome-actions">
            <button
              type="button"
              className="welcome-btn welcome-btn-primary"
              onClick={() => { setIsRegistering(false); setScreen("parent"); }}
            >
              Jag är förälder
            </button>
            <button
              type="button"
              className="welcome-btn welcome-btn-outline"
              onClick={() => setScreen("child-info")}
            >
              Jag är barn
            </button>
            <button
              type="button"
              className="welcome-btn welcome-btn-text"
              onClick={() => { setIsRegistering(false); setScreen("parent"); }}
            >
              Logga in
            </button>
          </div>

          <p className="welcome-privacy">
            <a href="/privacy">Integritetspolicy</a>
          </p>
        </div>
      </div>
    );
  }

  // ─── Child info screen ───────────────────────────────────────────────────────
  if (screen === "child-info") {
    return (
      <div className="welcome-screen">
        <div className="child-info-container">
          <button type="button" className="back-button" onClick={goBack} aria-label="Tillbaka">
            ←
          </button>

          <div className="child-info-header">
            <span className="child-info-emoji">👋</span>
            <h2 className="child-info-title">Hej! Välkommen till KidQuest</h2>
            <p className="child-info-intro">
              För att komma igång behöver en förälder eller vuxen skapa ett konto och bjuda in dig.
            </p>
          </div>

          <div className="child-info-steps">
            <div className="child-info-step">
              <div className="child-info-step-icon">1</div>
              <div className="child-info-step-content">
                <p className="child-info-step-title">En förälder skapar ett konto</p>
                <p className="child-info-step-desc">
                  En vuxen i familjen registrerar sig på KidQuest och sätter upp familjen.
                </p>
              </div>
            </div>

            <div className="child-info-step-arrow">↓</div>

            <div className="child-info-step">
              <div className="child-info-step-icon">2</div>
              <div className="child-info-step-content">
                <p className="child-info-step-title">Föräldern bjuder in dig</p>
                <p className="child-info-step-desc">
                  De skapar en inbjudan åt dig — antingen en <strong>QR-kod</strong> att scanna eller en <strong>länk</strong> att klicka på.
                </p>
              </div>
            </div>

            <div className="child-info-step-arrow">↓</div>

            <div className="child-info-step">
              <div className="child-info-step-icon">3</div>
              <div className="child-info-step-content">
                <p className="child-info-step-title">Du scannar eller klickar</p>
                <p className="child-info-step-desc">
                  Scanna QR-koden med kameran eller öppna länken — så är du inloggad direkt!
                </p>
              </div>
            </div>
          </div>

          <div className="child-info-tip">
            <span>💡</span>
            <p>Be din förälder öppna appen, gå till <strong>Familjemedlemmar</strong> och tryck på <strong>Bjud in barn</strong>.</p>
          </div>

          <div className="child-info-actions">
            <button
              type="button"
              className="welcome-btn welcome-btn-primary"
              onClick={() => setScreen("token")}
            >
              Jag har redan en inbjudningskod
            </button>
            <button
              type="button"
              className="welcome-btn welcome-btn-text"
              onClick={goBack}
            >
              Tillbaka till start
            </button>
          </div>
        </div>
      </div>
    );
  }

  // ─── Token login screen (child with code) ───────────────────────────────────
  if (screen === "token") {
    return (
      <div className="welcome-screen">
        <div className="login-register-container">
          <button type="button" className="back-button" onClick={() => setScreen("child-info")} aria-label="Tillbaka">
            ←
          </button>
          <div className="login-register-container .app-title" style={{ textAlign: "center", marginBottom: "24px" }}>
            <div style={{ fontSize: "2.5rem", marginBottom: "8px" }}>🔑</div>
            <h2 style={{ margin: 0, fontWeight: 700, fontSize: "1.3rem", color: "#1C1917" }}>Logga in med kod</h2>
            <p className="form-hint" style={{ marginTop: "6px" }}>
              Klistra in din inbjudningskod nedan.
            </p>
          </div>

          {error && <p className="error-text">{error}</p>}

          <form onSubmit={handleTokenLogin} className="auth-form">
            <div className="form-group">
              <label htmlFor="deviceToken">Inbjudningskod</label>
              <input
                id="deviceToken"
                type="text"
                value={deviceToken}
                onChange={(e) => setDeviceToken(e.target.value)}
                placeholder="Klistra in din kod här"
                required
                disabled={loading}
                autoFocus
              />
              <p className="form-hint">
                Du fick koden av din förälder, antingen via en länk eller QR-kod.
              </p>
            </div>
            <button type="submit" className="button-primary" disabled={loading}>
              {loading ? "Loggar in..." : "Logga in"}
            </button>
          </form>
        </div>
      </div>
    );
  }

  // ─── Parent login / register screen ─────────────────────────────────────────
  return (
    <div className="welcome-screen">
      <div className="login-register-container">
        <button type="button" className="back-button" onClick={goBack} aria-label="Tillbaka">
          ←
        </button>

        <div className="auth-tabs">
          <button
            type="button"
            className={`auth-tab ${!isRegistering ? "active" : ""}`}
            onClick={() => { setIsRegistering(false); setError(null); }}
          >
            Logga in
          </button>
          <button
            type="button"
            className={`auth-tab ${isRegistering ? "active" : ""}`}
            onClick={() => { setIsRegistering(true); setError(null); }}
          >
            Skapa konto
          </button>
        </div>

        {error && <p className="error-text">{error}</p>}

        {!isRegistering ? (
          <form onSubmit={handleEmailLogin} className="auth-form">
            <div className="form-group">
              <label htmlFor="email">E-postadress</label>
              <input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="din@epost.se"
                required
                disabled={loading}
                autoFocus
              />
            </div>
            <div className="form-group">
              <label htmlFor="password">Lösenord</label>
              <div style={{ position: "relative" }}>
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Ditt lösenord"
                  required
                  disabled={loading}
                  style={{ paddingRight: "40px", width: "100%", boxSizing: "border-box" }}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  style={{ position: "absolute", right: "8px", top: "50%", transform: "translateY(-50%)", background: "none", border: "none", cursor: "pointer", fontSize: "14px", color: "#666", padding: "4px 8px" }}
                  aria-label={showPassword ? "Dölj lösenord" : "Visa lösenord"}
                >
                  {showPassword ? "🙈" : "👁️"}
                </button>
              </div>
            </div>
            <button type="submit" className="button-primary" disabled={loading}>
              {loading ? "Loggar in..." : "Logga in"}
            </button>
          </form>
        ) : (
          <form onSubmit={handleRegister} className="auth-form">
            <div className="form-group">
              <label htmlFor="familyName">Familjenamn</label>
              <input
                id="familyName"
                type="text"
                value={familyName}
                onChange={(e) => setFamilyName(e.target.value)}
                placeholder="T.ex. Anderssons"
                required
                disabled={loading}
                autoFocus
              />
            </div>
            <div className="form-group">
              <label htmlFor="adminName">Ditt namn</label>
              <input
                id="adminName"
                type="text"
                value={adminName}
                onChange={(e) => setAdminName(e.target.value)}
                placeholder="T.ex. Anna"
                required
                disabled={loading}
              />
            </div>
            <div className="form-group">
              <label htmlFor="adminEmail">E-postadress</label>
              <input
                id="adminEmail"
                type="email"
                value={adminEmail}
                onChange={(e) => setAdminEmail(e.target.value)}
                placeholder="anna@example.com"
                required
                disabled={loading}
              />
            </div>
            <div className="form-group">
              <label htmlFor="registerPassword">Lösenord</label>
              <div style={{ position: "relative" }}>
                <input
                  id="registerPassword"
                  type={showRegisterPassword ? "text" : "password"}
                  value={registerPassword}
                  onChange={(e) => setRegisterPassword(e.target.value)}
                  placeholder="Minst 6 tecken"
                  required
                  minLength={6}
                  disabled={loading}
                  style={{ paddingRight: "40px", width: "100%", boxSizing: "border-box" }}
                />
                <button
                  type="button"
                  onClick={() => setShowRegisterPassword(!showRegisterPassword)}
                  style={{ position: "absolute", right: "8px", top: "50%", transform: "translateY(-50%)", background: "none", border: "none", cursor: "pointer", fontSize: "14px", color: "#666", padding: "4px 8px" }}
                  aria-label={showRegisterPassword ? "Dölj lösenord" : "Visa lösenord"}
                >
                  {showRegisterPassword ? "🙈" : "👁️"}
                </button>
              </div>
            </div>
            <div className="form-group">
              <label htmlFor="registerPasswordConfirm">Bekräfta lösenord</label>
              <div style={{ position: "relative" }}>
                <input
                  id="registerPasswordConfirm"
                  type={showRegisterPasswordConfirm ? "text" : "password"}
                  value={registerPasswordConfirm}
                  onChange={(e) => setRegisterPasswordConfirm(e.target.value)}
                  placeholder="Upprepa lösenordet"
                  required
                  minLength={6}
                  disabled={loading}
                  style={{ paddingRight: "40px", width: "100%", boxSizing: "border-box" }}
                />
                <button
                  type="button"
                  onClick={() => setShowRegisterPasswordConfirm(!showRegisterPasswordConfirm)}
                  style={{ position: "absolute", right: "8px", top: "50%", transform: "translateY(-50%)", background: "none", border: "none", cursor: "pointer", fontSize: "14px", color: "#666", padding: "4px 8px" }}
                  aria-label={showRegisterPasswordConfirm ? "Dölj lösenord" : "Visa lösenord"}
                >
                  {showRegisterPasswordConfirm ? "🙈" : "👁️"}
                </button>
              </div>
            </div>
            <button type="submit" className="button-primary" disabled={loading}>
              {loading ? "Registrerar..." : "Skapa familj"}
            </button>
            <p className="form-hint">
              Du kan sedan bjuda in barn och övriga familjemedlemmar via QR-kod.
            </p>
          </form>
        )}

        <p style={{ textAlign: "center", marginTop: "1rem", fontSize: "0.8rem", color: "#999" }}>
          <a href="/privacy" style={{ color: "#999" }}>Integritetspolicy</a>
        </p>
      </div>
    </div>
  );
}
