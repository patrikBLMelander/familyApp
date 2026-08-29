import type { ReactNode } from "react";

/**
 * Shared chrome for the pages a visitor can reach without logging in: the two legal
 * documents, account deletion, and the password reset form.
 *
 * They carry KidQuest's own palette rather than the web app's older look. These four
 * are the only pages someone meets outside the app — a parent checking what happens to
 * their children's data, or one locked out and following a link from their inbox — and
 * a page that does not resemble the app they are asking about is a page that invites
 * doubt at precisely the wrong moment.
 *
 * The tokens are lifted from the Android app: the lavender-to-sky gradient behind every
 * screen, the cream card, and #0C4A6E as the one strong colour.
 */

export const KID_QUEST = {
  gradient: "linear-gradient(180deg, #E0E7FF 0%, #E0F2FE 100%)",
  card: "#FFFBEB",
  textPrimary: "#1C1917",
  textSecondary: "#57534E",
  muted: "#78716C",
  accent: "#0C4A6E",
  rule: "#E7E5E4",
} as const;

export function LegalPage({
  title,
  updated,
  children,
}: {
  title: string;
  updated?: string;
  children: ReactNode;
}) {
  return (
    <PublicPage>
      <h1 style={{ fontSize: "1.55rem", lineHeight: 1.25, margin: "0 0 0.35rem" }}>{title}</h1>
      {updated && (
        <p style={{ color: KID_QUEST.muted, fontSize: "0.85rem", margin: "0 0 1.75rem" }}>
          Senast uppdaterad: {updated}
        </p>
      )}
      {children}
    </PublicPage>
  );
}

/** The gradient page and the cream card everything sits on. */
export function PublicPage({ children }: { children: ReactNode }) {
  return (
    <div
      style={{
        minHeight: "100vh",
        background: KID_QUEST.gradient,
        padding: "2.5rem 1rem 4rem",
        boxSizing: "border-box",
        fontFamily: "system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif",
        color: KID_QUEST.textPrimary,
      }}
    >
      <div style={{ maxWidth: 620, margin: "0 auto" }}>
        <div
          style={{
            fontSize: "0.95rem",
            fontWeight: 700,
            letterSpacing: "0.02em",
            color: KID_QUEST.accent,
            marginBottom: "1rem",
            paddingLeft: "0.25rem",
          }}
        >
          KidQuest
        </div>
        <div
          style={{
            background: KID_QUEST.card,
            borderRadius: 18,
            padding: "2rem 1.75rem 2.25rem",
            boxShadow: "0 1px 3px rgba(28,25,23,0.10), 0 1px 2px rgba(28,25,23,0.06)",
            lineHeight: 1.7,
          }}
        >
          {children}
        </div>
      </div>
    </div>
  );
}

export function LegalHeading({ children }: { children: ReactNode }) {
  return (
    <h2 style={{ fontSize: "1.05rem", marginTop: "1.75rem", marginBottom: "0.4rem" }}>
      {children}
    </h2>
  );
}

export function LegalContact() {
  return (
    <>
      <LegalHeading>Kontakt</LegalHeading>
      <p>
        Har du frågor? Jag svarar själv:{" "}
        <a href="mailto:patrikblmelander@gmail.com" style={{ color: KID_QUEST.accent }}>
          patrikblmelander@gmail.com
        </a>
      </p>
    </>
  );
}
