const petGradients: Record<string, { from: string; to: string }> = {
  dragon:   { from: "#4C1D95", to: "#1E293B" },
  cat:      { from: "#FDE68A", to: "#F97316" },
  dog:      { from: "#BBF7D0", to: "#22C55E" },
  bird:     { from: "#BFDBFE", to: "#2563EB" },
  rabbit:   { from: "#FCE7F3", to: "#EC4899" },
  bear:     { from: "#FEF3C7", to: "#92400E" },
  snake:    { from: "#DCFCE7", to: "#15803D" },
  panda:    { from: "#E5E7EB", to: "#111827" },
  slot:     { from: "#E5E7EB", to: "#6B7280" },
  hydra:    { from: "#C4B5FD", to: "#4C1D95" },
  unicorn:  { from: "#FDE68A", to: "#F9A8D4" },
  kapybara: { from: "#DCFCE7", to: "#22C55E" },
  shark:    { from: "#BAE6FD", to: "#0369A1" },
  lion:     { from: "#FEF3C7", to: "#D97706" },
};

const defaultGradient = { from: "#E0E7FF", to: "#E0F2FE" };

// Pets whose background is dark enough to require white text
const darkThemePets = new Set(["dragon"]);

export function getPetGradient(petType: string): { from: string; to: string } {
  return petGradients[petType?.toLowerCase()] ?? defaultGradient;
}

export function isDarkPetTheme(petType: string): boolean {
  return darkThemePets.has(petType?.toLowerCase() ?? "");
}
