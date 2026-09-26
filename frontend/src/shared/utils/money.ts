/** Default when the family has no currency yet (or an older backend doesn't send one). */
export const DEFAULT_CURRENCY = "SEK";

// The web UI is Swedish, so sv-SE formatting: "120 kr", "120 €", "120 US$".
const LOCALE = "sv-SE";

function formatter(currency: string): Intl.NumberFormat {
  try {
    return new Intl.NumberFormat(LOCALE, {
      style: "currency",
      currency,
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    });
  } catch {
    // An unknown code must not break the page.
    return formatter(DEFAULT_CURRENCY);
  }
}

/** Formats whole-unit wallet money in the family's currency. */
export function formatMoney(amount: number, currency: string = DEFAULT_CURRENCY): string {
  return formatter(currency).format(amount);
}

/** Just the symbol ("kr", "€"), for input labels like "Belopp (kr)". */
export function currencySymbol(currency: string = DEFAULT_CURRENCY): string {
  return formatter(currency).formatToParts(0).find(part => part.type === "currency")?.value ?? currency;
}
