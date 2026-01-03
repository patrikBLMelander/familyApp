import { useEffect, useState } from "react";
import { linkDeviceByInviteToken } from "../../shared/api/familyMembers";

export function InviteView() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  useEffect(() => {
    const handleInvite = async () => {
      // Get token from URL path (e.g., /invite/abc123)
      const pathParts = window.location.pathname.split("/");
      const token = pathParts[pathParts.length - 1];

      if (!token || token === "invite") {
        setError("Ingen inbjudanstoken hittades.");
        setLoading(false);
        return;
      }

      try {
        // Generate a device token for this device (simple: use a UUID stored in localStorage)
        let deviceToken = localStorage.getItem("deviceToken");
        if (!deviceToken) {
          // Generate a simple device token (in production, use a proper UUID generator)
          deviceToken = `device_${Date.now()}_${Math.random().toString(36).substring(7)}`;
          localStorage.setItem("deviceToken", deviceToken);
        }

        // Link this device to the member using the invite token
        await linkDeviceByInviteToken(token, deviceToken);
        
        setSuccess(true);
        // Redirect to daily tasks after 2 seconds
        setTimeout(() => {
          window.location.href = "/";
        }, 2000);
      } catch (e) {
        setError("Kunde inte koppla enheten. Kontrollera att QR-koden är korrekt.");
      } finally {
        setLoading(false);
      }
    };

    void handleInvite();
  }, []);

  if (loading) {
    return (
      <div className="invite-view">
        <section className="card">
          <p>Kopplar enhet...</p>
        </section>
      </div>
    );
  }

  if (error) {
    return (
      <div className="invite-view">
        <section className="card">
          <p className="error-text">{error}</p>
        </section>
      </div>
    );
  }

  if (success) {
    return (
      <div className="invite-view">
        <section className="card">
          <h2>Enhet kopplad!</h2>
          <p>Din enhet är nu kopplad. Du omdirigeras till dagens sysslor...</p>
        </section>
      </div>
    );
  }

  return null;
}

