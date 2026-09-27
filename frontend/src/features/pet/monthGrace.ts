import type { MonthInfoResponse } from "../../shared/api/pets";

/** Days-left threshold for the egg picker banner; matches the backend's first-pet grace window. */
export const MONTH_END_NOTICE_DAYS = 10;

const MONTH_NAMES = [
  "januari", "februari", "mars", "april", "maj", "juni",
  "juli", "augusti", "september", "oktober", "november", "december",
];

/** Lowercase Swedish name for a 1-12 month. */
export function swedishMonthName(month: number): string {
  return MONTH_NAMES[(month - 1 + 12) % 12];
}

/** The egg picker's month-end banner, or null when the month isn't ending soon. */
export function monthEndNotice(info: MonthInfoResponse): string | null {
  if (info.daysLeftInMonth > MONTH_END_NOTICE_DAYS) {
    return null;
  }
  const endsIn = info.daysLeftInMonth === 0
    ? "Månaden tar slut idag"
    : `Månaden tar slut om ${info.daysLeftInMonth} ${info.daysLeftInMonth === 1 ? "dag" : "dagar"}`;
  const next = swedishMonthName(info.nextMonth);
  return info.firstPetGrace
    ? `${endsIn} – men ditt första ägg följer med dig hela ${next}! 🥚`
    : `${endsIn} – ett nytt ägg väntar 1 ${next}.`;
}

/** Badge text for a pet that follows into next month (pet.month is 1-12). */
export function followsIntoNextMonthLabel(petMonth: number): string {
  return `Följer med dig hela ${swedishMonthName(petMonth + 1)} 🥚`;
}
