import type { ReactNode } from "react";

/**
 * Shared chrome for the two public legal pages, so Villkor and Integritetspolicy
 * cannot drift apart visually. Two documents that look like they come from different
 * places is not the impression you want at the moment someone is deciding whether to
 * trust you with their family's data.
 */
export function LegalPage({
  title,
  updated,
  children,
}: {
  title: string;
  updated: string;
  children: ReactNode;
}) {
  return (
    <div
      style={{
        maxWidth: 600,
        margin: "0 auto",
        padding: "2rem 1.5rem",
        lineHeight: 1.7,
        color: "#333",
      }}
    >
      <h1 style={{ fontSize: "1.6rem", marginBottom: "0.5rem" }}>{title}</h1>
      <p style={{ color: "#888", marginBottom: "2rem" }}>Senast uppdaterad: {updated}</p>
      {children}
    </div>
  );
}

export function LegalHeading({ children }: { children: ReactNode }) {
  return <h2 style={{ fontSize: "1.1rem", marginTop: "1.5rem" }}>{children}</h2>;
}

export function LegalContact() {
  return (
    <>
      <LegalHeading>Kontakt</LegalHeading>
      <p>
        Har du frågor? Jag svarar själv:{" "}
        <a href="mailto:patrikblmelander@gmail.com">patrikblmelander@gmail.com</a>
      </p>
    </>
  );
}
