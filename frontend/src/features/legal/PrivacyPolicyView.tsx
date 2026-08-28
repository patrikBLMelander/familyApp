import { LegalPage, LegalHeading, LegalContact } from "./LegalPage";

/**
 * Integritetspolicy, at /privacy.
 *
 * Rewritten on the honest strength of the data model rather than on reassuring
 * generalities: a child in KidQuest is a name a parent typed and a random token. There
 * is no birthdate, no age and no e-mail for a child anywhere in the schema, and the age
 * picker in "lägg till barn" only chooses which default chores to create -- it is never
 * stored. Saying that plainly is worth more than any promise about careful handling.
 *
 * Two things the previous version left out and needed: the menstrual cycle feature,
 * which is health data and therefore a special category under GDPR, and the payment
 * processors that a subscription brings with it.
 *
 * The cycle feature exists only in the web app -- there is no menstrual code in
 * android/ or ios/. It is covered here because the web app is live and the table can
 * hold rows today, not because it is part of the product's future. When the web app is
 * retired in favour of the two mobile apps, delete the menstrual_cycle rows and the
 * table along with it, and only then remove this section. Dropping the section while
 * the data still exists would make this document untrue.
 */
export function PrivacyPolicyView() {
  return (
    <LegalPage title="Integritetspolicy för KidQuest" updated="augusti 2026">
      <p>
        KidQuest utvecklas och drivs av mig, Patrik Melander, som privatperson. Jag är
        personuppgiftsansvarig för uppgifterna i appen. Den här sidan beskriver vad som
        sparas, varför, och vad du kan göra åt det.
      </p>

      <LegalHeading>Om barn: nästan ingenting</LegalHeading>
      <p>
        Det här är den viktigaste punkten. Om ett barn sparas bara:
      </p>
      <ul>
        <li>
          <strong>Ett namn som du som förälder skriver in.</strong> Det behöver inte vara
          barnets riktiga namn. Ett smeknamn eller en initial fungerar precis lika bra.
        </li>
        <li>
          <strong>En slumpmässig kod</strong> som kopplar barnets telefon till familjen.
        </li>
      </ul>
      <p>
        Det finns <strong>ingen födelsedag, ingen ålder, ingen e-postadress</strong> och
        inget personnummer för barn — inte någonstans. När du väljer ålder vid tillägg av
        ett barn används det bara för att föreslå passande sysslor. Valet sparas aldrig.
      </p>
      <p>
        Det betyder att uppgifterna om ett barn inte går att koppla till en verklig person
        av någon som skulle få tag på dem. Det är medvetet.
      </p>

      <LegalHeading>Om vuxna</LegalHeading>
      <p>
        En vuxen som skapar familjen sparas med namn, e-postadress och ett krypterat
        lösenord. E-postadressen används för att logga in — inte för utskick. Lösenord
        lagras hashade med BCrypt och loggas aldrig i klartext.
      </p>

      <LegalHeading>Vad familjen själv lägger in</LegalHeading>
      <p>
        Sysslor, listor, kalenderhändelser, XP, djur och plånbokstransaktioner sparas för
        att appen ska fungera. Inget av det används till något annat.
      </p>

      <LegalHeading>Mensspårning (endast webbversionen)</LegalHeading>
      <p>
        Webbversionen har en funktion för att följa sin mens. Den finns inte i
        mobilapparna. Den är <strong>avstängd som standard</strong>, kan bara slås på av en
        vuxen för sig själv, och är privat som standard — andra i familjen ser den inte.
      </p>
      <p>
        Uppgifter om mens är hälsouppgifter, vilket enligt GDPR är en särskild kategori av
        personuppgifter. Rättslig grund är ditt uttryckliga samtycke, som du ger genom att
        själv slå på funktionen. Slår du av den och tar bort dina inlägg finns de inte kvar.
      </p>

      <LegalHeading>Var lagras uppgifterna</LegalHeading>
      <p>
        Familjens uppgifter lagras på servrar i <strong>Amsterdam, Nederländerna</strong> —
        alltså inom EU.
      </p>

      <LegalHeading>Vilka andra får se uppgifterna</LegalHeading>
      <p>
        Jag säljer inga uppgifter och delar dem inte för marknadsföring. Följande
        leverantörer behandlar uppgifter för att appen ska kunna fungera:
      </p>
      <ul>
        <li>
          <strong>Railway</strong> — driver servern och databasen, i EU enligt ovan.
        </li>
        <li>
          <strong>Google Play</strong> — hanterar betalning av prenumerationen. Google får
          uppgifter om köpet direkt från dig; jag ser aldrig ditt kortnummer.
        </li>
        <li>
          <strong>RevenueCat</strong> — håller reda på om familjen har en aktiv
          prenumeration. RevenueCat får familjens interna id-nummer och information om
          köpet — inte namn, inte e-postadresser, och ingenting om barnen.
        </li>
      </ul>
      <p>
        Google och RevenueCat är amerikanska företag, så uppgifterna om själva köpet
        behandlas utanför EU. Det som lämnar EU är familjens id-nummer och information om
        prenumerationen. Namn, e-postadresser och allt som rör barnen stannar i EU.
      </p>

      <LegalHeading>Hur länge sparas det</LegalHeading>
      <p>
        Så länge familjen finns i appen. Raderar du familjen tas uppgifterna bort, och
        barn, sysslor, djur och plånbokshistorik följer med.
      </p>

      <LegalHeading>Dina rättigheter</LegalHeading>
      <p>
        Du har rätt att få ut, rätta eller radera dina personuppgifter, att invända mot
        behandlingen och att ta tillbaka ett samtycke du gett. Hör av dig så ordnar jag
        det. Är du inte nöjd med hur jag hanterat det kan du vända dig till
        Integritetsskyddsmyndigheten (IMY).
      </p>

      <LegalContact />
    </LegalPage>
  );
}
