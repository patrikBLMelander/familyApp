/**
 * Explains a 402 on the web, once, for a client that has no API layer to put it in.
 *
 * Every function in shared/api/ calls fetch directly, so there is no single place to
 * handle a status code. Threading one through several dozen functions is not worth it
 * for a client being retired in favour of the two mobile apps -- but leaving it out
 * means an expired family sees "Kunde inte lägga till" and no reason why, which is the
 * worst version of a paywall: a wall with no sign on it.
 *
 * So: one wrapper around fetch, installed once, that notices 402 and says what is
 * happening. Deliberately not a React component -- it has to work regardless of which
 * view made the call, and it has no state to keep.
 *
 * When the web app goes, this goes with it.
 */

const BANNER_ID = "kidquest-payment-required";

function showNotice() {
  if (document.getElementById(BANNER_ID)) {
    return;
  }
  const banner = document.createElement("div");
  banner.id = BANNER_ID;
  banner.setAttribute("role", "status");
  banner.style.cssText = [
    "position:fixed",
    "left:0",
    "right:0",
    "top:0",
    "z-index:9999",
    "background:#FEF2F2",
    "color:#991B1B",
    "border-bottom:1px solid #FECACA",
    "padding:12px 16px",
    "font:14px/1.5 system-ui,sans-serif",
    "display:flex",
    "gap:12px",
    "align-items:flex-start",
  ].join(";");

  const text = document.createElement("div");
  text.style.flex = "1";
  text.innerHTML =
    "<strong>Provperioden har gått ut.</strong> Barnens sysslor, djur och plånbok " +
    "fungerar som vanligt, men för att lägga till eller ändra behöver familjen en " +
    "prenumeration. Du förnyar i KidQuest-appen på din telefon.";

  const dismiss = document.createElement("button");
  dismiss.type = "button";
  dismiss.textContent = "Stäng";
  dismiss.style.cssText =
    "background:none;border:1px solid #FECACA;color:#991B1B;border-radius:6px;padding:4px 10px;cursor:pointer";
  dismiss.onclick = () => banner.remove();

  banner.append(text, dismiss);
  document.body.prepend(banner);
}

/** Call once, at start-up. Safe to call twice; the second call does nothing. */
export function installPaymentRequiredNotice() {
  const w = window as Window & { __kidquestPaymentNoticeInstalled?: boolean };
  if (w.__kidquestPaymentNoticeInstalled) {
    return;
  }
  w.__kidquestPaymentNoticeInstalled = true;

  const original = window.fetch.bind(window);
  window.fetch = async (...args: Parameters<typeof fetch>) => {
    const response = await original(...args);
    if (response.status === 402) {
      showNotice();
    }
    return response;
  };
}
