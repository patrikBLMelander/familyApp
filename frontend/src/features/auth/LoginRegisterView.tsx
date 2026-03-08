import { useState } from "react";
import { registerFamily, loginByEmail } from "../../shared/api/family";
import { getMemberByDeviceToken } from "../../shared/api/familyMembers";

type LoginRegisterViewProps = {
  onLogin: (deviceToken: string) => void;
};

export function LoginRegisterView({ onLogin }: LoginRegisterViewProps) {
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
  const [useEmailLogin, setUseEmailLogin] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    // Validate password
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
      // Store device token
      localStorage.setItem("deviceToken", result.deviceToken);
      // Redirect to dashboard
      onLogin(result.deviceToken);
    } catch (e) {
      setError("Kunde inte registrera familj. Försök igen.");
      console.error("Registration error:", e);
    } finally {
      setLoading(false);
    }
  };

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      if (useEmailLogin) {
        // Login with email and password
        if (!password) {
          setError("Lösenord krävs för inloggning med e-post.");
          setLoading(false);
          return;
        }
        const result = await loginByEmail(email, password);
        localStorage.setItem("deviceToken", result.deviceToken);
        onLogin(result.deviceToken);
      } else {
        // Login with device token
        await getMemberByDeviceToken(deviceToken);
        localStorage.setItem("deviceToken", deviceToken);
        onLogin(deviceToken);
      }
    } catch (e) {
      if (useEmailLogin) {
        const errorMessage = e instanceof Error ? e.message : String(e);
        if (errorMessage.includes("password") || errorMessage.includes("Password")) {
          setError("Felaktigt lösenord. Försök igen.");
        } else if (errorMessage.includes("not set") || errorMessage.includes("Please set a password")) {
          setError("Lösenord är inte satt för detta konto. Logga in med din inloggningstoken och sätt ett lösenord under Familjemedlemmar.");
        } else {
          setError("Felaktig e-postadress eller lösenord. Endast vuxna kan logga in med e-post.");
        }
      } else {
        setError("Ogiltig inloggningstoken. Kontrollera att token är korrekt.");
      }
      console.error("Login error:", e);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-register-view">
      <div className="login-register-container">
        <h1 className="app-title">FamilyApp</h1>
        
        <div className="auth-tabs">
          <button
            type="button"
            className={`auth-tab ${!isRegistering ? "active" : ""}`}
            onClick={() => setIsRegistering(false)}
          >
            Logga in
          </button>
          <button
            type="button"
            className={`auth-tab ${isRegistering ? "active" : ""}`}
            onClick={() => setIsRegistering(true)}
          >
            Registrera
          </button>
        </div>

        {error && <p className="error-text">{error}</p>}

        {!isRegistering ? (
          <form onSubmit={handleLogin} className="auth-form">
            <div style={{ marginBottom: "16px", display: "flex", gap: "8px" }}>
              <button
                type="button"
                className={`button-secondary ${!useEmailLogin ? "active" : ""}`}
                onClick={() => {
                  setUseEmailLogin(false);
                  setPassword(""); // Clear password when switching to token login
                }}
                style={{ flex: 1 }}
              >
                Token
              </button>
              <button
                type="button"
                className={`button-secondary ${useEmailLogin ? "active" : ""}`}
                onClick={() => {
                  setUseEmailLogin(true);
                  setDeviceToken(""); // Clear device token when switching to email login
                }}
                style={{ flex: 1 }}
              >
                E-post
              </button>
            </div>
            
            {!useEmailLogin ? (
              <>
                <div className="form-group">
                  <label htmlFor="deviceToken">Inloggningstoken</label>
                  <input
                    id="deviceToken"
                    type="text"
                    value={deviceToken}
                    onChange={(e) => setDeviceToken(e.target.value)}
                    placeholder="Klistra in din inloggningstoken"
                    required
                    disabled={loading}
                  />
                  <p className="form-hint">
                    Använd din inloggningstoken för att logga in. Du får den när du registrerar en familj eller när någon bjuder in dig.
                  </p>
                </div>
              </>
            ) : (
              <>
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
                      style={{ paddingRight: "40px", width: "100%" }}
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      style={{
                        position: "absolute",
                        right: "8px",
                        top: "50%",
                        transform: "translateY(-50%)",
                        background: "none",
                        border: "none",
                        cursor: "pointer",
                        fontSize: "14px",
                        color: "#666",
                        padding: "4px 8px"
                      }}
                      aria-label={showPassword ? "Dölj lösenord" : "Visa lösenord"}
                    >
                      {showPassword ? "🙈" : "👁️"}
                    </button>
                  </div>
                </div>
                <p className="form-hint">
                  Logga in med din e-postadress och lösenord (för vuxna och äldre barn). En ny inloggningstoken genereras automatiskt.
                </p>
              </>
            )}
            
            <button type="submit" className="button-primary" disabled={loading}>
              {loading ? "Loggar in..." : "Logga in"}
            </button>
            <p style={{ textAlign: "center", marginTop: "1rem", fontSize: "0.8rem", color: "#999" }}>
              <a href="/privacy" style={{ color: "#999" }}>Integritetspolicy</a>
            </p>
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
              <label htmlFor="adminEmail">Din e-postadress</label>
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
                  style={{ paddingRight: "40px", width: "100%" }}
                />
                <button
                  type="button"
                  onClick={() => setShowRegisterPassword(!showRegisterPassword)}
                  style={{
                    position: "absolute",
                    right: "8px",
                    top: "50%",
                    transform: "translateY(-50%)",
                    background: "none",
                    border: "none",
                    cursor: "pointer",
                    fontSize: "14px",
                    color: "#666",
                    padding: "4px 8px"
                  }}
                  aria-label={showRegisterPassword ? "Dölj lösenord" : "Visa lösenord"}
                >
                  {showRegisterPassword ? "🙈" : "👁️"}
                </button>
              </div>
              <p className="form-hint">
                Lösenordet måste vara minst 6 tecken långt.
              </p>
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
                  style={{ paddingRight: "40px", width: "100%" }}
                />
                <button
                  type="button"
                  onClick={() => setShowRegisterPasswordConfirm(!showRegisterPasswordConfirm)}
                  style={{
                    position: "absolute",
                    right: "8px",
                    top: "50%",
                    transform: "translateY(-50%)",
                    background: "none",
                    border: "none",
                    cursor: "pointer",
                    fontSize: "14px",
                    color: "#666",
                    padding: "4px 8px"
                  }}
                  aria-label={showRegisterPasswordConfirm ? "Dölj lösenord" : "Visa lösenord"}
                >
                  {showRegisterPasswordConfirm ? "🙈" : "👁️"}
                </button>
              </div>
            </div>
            <button type="submit" className="button-primary" disabled={loading}>
              {loading ? "Registrerar..." : "Registrera familj"}
            </button>
            <p className="form-hint">
              När du registrerar en familj skapas en ny familj med dig som huvudanvändare. Du kan sedan bjuda in andra familjemedlemmar via QR-kod.
            </p>
          </form>
        )}
      </div>
    </div>
  );
}

