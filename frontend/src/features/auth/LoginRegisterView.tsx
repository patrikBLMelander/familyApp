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
  const [useEmailLogin, setUseEmailLogin] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      const result = await registerFamily(familyName, adminName, adminEmail);
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
        // Login with email
        const result = await loginByEmail(email);
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
        setError("Ingen användare hittades med denna e-postadress. Endast admin-användare kan logga in med e-post.");
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
                onClick={() => setUseEmailLogin(false)}
                style={{ flex: 1 }}
              >
                Token
              </button>
              <button
                type="button"
                className={`button-secondary ${useEmailLogin ? "active" : ""}`}
                onClick={() => setUseEmailLogin(true)}
                style={{ flex: 1 }}
              >
                E-post (Admin)
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
                <p className="form-hint">
                  Logga in med din e-postadress (endast för admin-användare). En ny inloggningstoken genereras automatiskt.
                </p>
              </div>
            )}
            
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
              <p className="form-hint">
                Din e-postadress används för att logga in om du tappar bort din inloggningstoken.
              </p>
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

