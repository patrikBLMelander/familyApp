import { LegalPage, LegalHeading, LegalContact } from "./LegalPage";

/**
 * Subscription terms, at /villkor.
 *
 * Google requires a subscription app to state its terms plainly, and the paywall links
 * here. The tone matches the paywall on purpose: this is one person's app, and the
 * terms should read like that person wrote them rather than like a template.
 */
export function TermsView() {
  return (
    <LegalPage title="Villkor för KidQuest" updated="augusti 2026">
      <p>
        KidQuest är en app som hjälper familjer med dagliga sysslor. Barn samlar mat till
        ett djur genom att göra sina uppgifter, och en förälder sköter listor, familjen och
        barnens digitala plånbok. Appen utvecklas och drivs av mig, Patrik Melander, som
        privatperson.
      </p>

      <LegalHeading>Konto</LegalHeading>
      <p>
        En vuxen skapar familjen med sin e-postadress och blir ansvarig för den. Barn får
        inget eget konto med e-post — de kopplas till familjen med en kod från en förälder.
        Du ansvarar för att hålla ditt lösenord för dig själv, och för det som görs i din
        familj.
      </p>

      <LegalHeading>Provperiod och pris</LegalHeading>
      <p>
        Varje familj får <strong>tre månader utan kostnad</strong>. Ingen betalningsmetod
        behövs för att börja, och provperioden startar när familjen börjar använda appen.
      </p>
      <p>
        Därefter kostar KidQuest <strong>29 kronor per månad</strong> för hela familjen,
        inklusive moms. Prenumerationen gäller familjen — inte per barn och inte per telefon.
      </p>

      <LegalHeading>Betalning och förnyelse</LegalHeading>
      <p>
        Betalning sker via Google Play, inte till mig direkt. Prenumerationen förnyas
        automatiskt varje månad tills du avslutar den. Priset dras av Google vid varje
        förnyelse.
      </p>
      <p>
        Om en betalning misslyckas fortsätter appen fungera medan Google försöker igen.
        Först när den perioden tagit slut upphör prenumerationen.
      </p>

      <LegalHeading>Avsluta</LegalHeading>
      <p>
        Du avslutar när du vill i Google Play, under Prenumerationer. Du behåller tiden du
        redan betalat för — appen slutar inte fungera samma dag du säger upp den.
      </p>

      <LegalHeading>Vad som händer utan prenumeration</LegalHeading>
      <p>
        Ingenting raderas. Barnens sysslor, djur, XP och plånbok finns kvar och fungerar
        som vanligt — ett barn ska inte förlora sitt djur för att en vuxen inte betalat.
      </p>
      <p>
        Det som pausas är föräldrarnas administration: att lägga till eller ändra sysslor,
        lägga till familjemedlemmar och hantera plånboken. Allt går att fortsätta med igen
        så snart en prenumeration finns.
      </p>

      <LegalHeading>Återbetalning</LegalHeading>
      <p>
        Köp görs genom Google Play, så Googles regler för återbetalning gäller och en
        begäran hanteras av dem. Som konsument i EU har du dessutom de rättigheter lagen
        ger dig. Hör av dig om något blivit fel, så hjälper jag till så gott jag kan.
      </p>

      <LegalHeading>Innehåll du lägger in</LegalHeading>
      <p>
        Namn, sysslor, listor och anteckningar är dina. Jag använder dem bara för att köra
        appen. Lägg inte in mer om dina barn än du behöver — appen kräver inget riktigt
        namn, och det är helt i sin ordning att skriva ett smeknamn.
      </p>

      <LegalHeading>Tillgänglighet och ansvar</LegalHeading>
      <p>
        Jag utvecklar KidQuest på min fritid och kan inte garantera att appen alltid är
        uppe eller felfri. Jag gör mitt bästa för att ta backuper och undvika dataförlust,
        men KidQuest bör inte vara den enda platsen där något viktigt finns sparat.
      </p>
      <p>
        Mitt ansvar är begränsat till vad du betalat för de senaste tolv månaderna. Det som
        följer av tvingande konsumentlagstiftning gäller ändå.
      </p>

      <LegalHeading>Ändringar</LegalHeading>
      <p>
        Appen utvecklas, så funktioner kan komma att ändras. Om priset ändras eller
        villkoren ändras på ett sätt som påverkar dig får du veta det i förväg, och du kan
        alltid avsluta innan en ändring börjar gälla.
      </p>

      <LegalHeading>Om jag behöver stänga ett konto</LegalHeading>
      <p>
        Jag kan stänga av ett konto som används för att skada någon annan eller för att
        missbruka tjänsten. Det har inte hänt, och jag hoppas det aldrig gör det.
      </p>

      <LegalHeading>Tillämplig lag</LegalHeading>
      <p>Svensk lag gäller för dessa villkor.</p>

      <LegalContact />
    </LegalPage>
  );
}
