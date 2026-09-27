import { useEffect, useState } from "react";
import { getFamily } from "../api/family";
import { getMemberByDeviceToken } from "../api/familyMembers";
import { DEFAULT_CURRENCY } from "../utils/money";

// Fetched once per page load and shared by every wallet view. A parent changes the
// currency in the mobile apps, so a reload picking it up is good enough here.
let currencyPromise: Promise<string> | null = null;

async function loadCurrency(): Promise<string> {
  const deviceToken = localStorage.getItem("deviceToken");
  if (!deviceToken) {
    return DEFAULT_CURRENCY;
  }
  const member = await getMemberByDeviceToken(deviceToken);
  if (!member.familyId) {
    return DEFAULT_CURRENCY;
  }
  const family = await getFamily(member.familyId);
  return family.currency ?? DEFAULT_CURRENCY;
}

/** The family's wallet currency; "SEK" while loading or if the lookup fails. */
export function useFamilyCurrency(): string {
  const [currency, setCurrency] = useState(DEFAULT_CURRENCY);

  useEffect(() => {
    let active = true;
    if (!currencyPromise) {
      currencyPromise = loadCurrency().catch(() => {
        currencyPromise = null; // try again next time rather than caching the failure
        return DEFAULT_CURRENCY;
      });
    }
    void currencyPromise.then(value => {
      if (active) {
        setCurrency(value);
      }
    });
    return () => {
      active = false;
    };
  }, []);

  return currency;
}
