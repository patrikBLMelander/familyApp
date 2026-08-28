import { LegalPage, LegalHeading, LegalContact } from "./LegalPage";

/**
 * Account deletion, at /radera-konto.
 *
 * Google Play requires two separate things of an app that lets people create an
 * account: a route to deletion inside the app, and a publicly reachable URL where
 * someone can request it. This is the second. It exists to be linked from the Data
 * safety form, and to be useful to anyone who cannot get into the app -- a lost phone,
 * a forgotten password -- which is exactly the person an in-app-only path fails.
 */
export function DeleteAccountView() {
  return (
    <LegalPage title="Radera ditt KidQuest-konto" updated="augusti 2026">
      <p>
        Du kan radera din familj och allt som hör till den, när du vill. Det finns två
        vägar: i appen, eller genom att mejla mig.
      </p>

      <LegalHeading>I appen</LegalHeading>
      <p>
        Öppna KidQuest, tryck på de tre punkterna längst upp till höger på
        <em> Min familj</em>, och välj <strong>Ta bort familjen</strong>. Du får skriva
        ett ord för att bekräfta. Sedan är det gjort.
      </p>
      <p>Det är en förälder som kan radera familjen — inte ett barn.</p>

      <LegalHeading>Om du inte kommer in i appen</LegalHeading>
      <p>
        Har du tappat telefonen eller glömt lösenordet: mejla mig från den adress du
        registrerade familjen med, och skriv att du vill radera kontot. Jag gör det för
        dig och svarar när det är klart. Jag brukar hinna inom några dagar.
      </p>

      <LegalHeading>Vad som raderas</LegalHeading>
      <p>
        Allt. Familjen, alla vuxna och barn, sysslor och deras historik, XP, djur och
        djurhistorik, plånböcker, transaktioner och sparmål, kalenderhändelser och
        listor.
      </p>
      <p>
        Ingenting sparas i en papperskorg och ingenting går att återställa efteråt. Det
        finns ingen ångerknapp, så var säker innan du gör det.
      </p>

      <LegalHeading>Prenumerationen</LegalHeading>
      <p>
        En prenumeration hör till ditt Google-konto och inte till familjen, så den
        försvinner <strong>inte</strong> automatiskt. Avsluta den separat i Google Play
        under Prenumerationer, annars fortsätter den förnyas.
      </p>

      <LegalContact />
    </LegalPage>
  );
}
