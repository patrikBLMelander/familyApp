export function PrivacyPolicyView() {
  return (
    <div style={{ maxWidth: 600, margin: "0 auto", padding: "2rem 1.5rem", lineHeight: 1.7, color: "#333" }}>
      <h1 style={{ fontSize: "1.6rem", marginBottom: "0.5rem" }}>Integritetspolicy för KidQuest</h1>
      <p style={{ color: "#888", marginBottom: "2rem" }}>Senast uppdaterad: mars 2026</p>

      <h2 style={{ fontSize: "1.1rem", marginTop: "1.5rem" }}>Vilka uppgifter samlar vi in?</h2>
      <p>
        KidQuest samlar in e-postadress och lösenord för vuxna användare.
        Barn identifieras via en enhetsunik token och ingen personligt identifierbar
        information samlas in direkt om barn.
      </p>

      <h2 style={{ fontSize: "1.1rem", marginTop: "1.5rem" }}>Hur används uppgifterna?</h2>
      <p>
        Uppgifterna används enbart för att tillhandahålla appens funktioner, såsom
        uppgiftshantering, kalender och digital plånbok inom familjen.
        Vi säljer eller delar inte dina uppgifter med tredje part.
      </p>

      <h2 style={{ fontSize: "1.1rem", marginTop: "1.5rem" }}>Lagring och säkerhet</h2>
      <p>
        Data lagras säkert på vår server. Lösenord lagras krypterade och loggas aldrig
        i klartext. Vi använder enhetens lokala lagring för att hålla dig inloggad.
      </p>

      <h2 style={{ fontSize: "1.1rem", marginTop: "1.5rem" }}>Barn</h2>
      <p>
        Appen är utformad för familjer. Barnprofiler skapas och hanteras av en förälder
        eller vårdnadshavare. Vi samlar inte medvetet in personuppgifter direkt från barn.
      </p>

      <h2 style={{ fontSize: "1.1rem", marginTop: "1.5rem" }}>Dina rättigheter</h2>
      <p>
        Du har rätt att begära ut, korrigera eller radera dina personuppgifter när som helst.
        Kontakta oss så hjälper vi dig.
      </p>

      <h2 style={{ fontSize: "1.1rem", marginTop: "1.5rem" }}>Kontakt</h2>
      <p>
        Har du frågor om denna integritetspolicy? Kontakta oss på:{" "}
        <a href="mailto:patrikblmelander@gmail.com">patrikblmelander@gmail.com</a>
      </p>
    </div>
  );
}
