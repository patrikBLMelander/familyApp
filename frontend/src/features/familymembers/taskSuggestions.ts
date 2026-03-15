export type AgeGroup = "4-6" | "7-9" | "10-12" | "13+";

export const AGE_GROUPS: { value: AgeGroup; label: string }[] = [
  { value: "4-6", label: "4–6 år" },
  { value: "7-9", label: "7–9 år" },
  { value: "10-12", label: "10–12 år" },
  { value: "13+", label: "13+ år" },
];

export const TASK_SUGGESTIONS: Record<AgeGroup, string[]> = {
  "4-6": [
    "Klä på mig",
    "Borsta tänder",
    "Plocka leksaker i mitt rum",
    "Ställ undan min disk",
    "Hänga upp jacka & skor",
  ],
  "7-9": [
    "Packa skolväskan",
    "Bädda sängen",
    "Plocka undan efter mellis",
    "Kvällsrutin utan tjat",
    "Hjälpa till med disk/dukning",
  ],
  "10-12": [
    "Läx-/pluggstund",
    "Skräpkoll hemma",
    "Ordning på rummet",
    "Hjälpa till med maten",
    "Skärm efter uppgifter",
  ],
  "13+": [
    "Hålla rummet i ordning",
    "Ta hand om min tvätt",
    "Min dagliga hemmasyssla",
    "Kolla dagens schema & tider",
    "Kolla ekonomi & sparmål",
  ],
};
