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
 * The other thing the previous version left out: the payment processors a subscription
 * brings with it.
 *
 * There is deliberately nothing here about menstrual cycle tracking. That feature was
 * web-only, and V44 removed both it and its data rather than carry special-category
 * health data for a feature nobody would reach -- so this document has nothing to
 * declare about it. The section came out only after the data did.
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
