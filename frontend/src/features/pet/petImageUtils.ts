/**
 * Utility functions for pet image handling.
 * Handles integrated images (pet + background combined) with fallback to separate images.
 */

/**
 * Get the path for an integrated pet image (pet + background combined).
 * Format: /pets/{petType}/{petType}-integrated-stage{stage}.png
 */
export function getIntegratedPetImagePath(petType: string, growthStage: number): string {
  const normalizedPetType = petType.toLowerCase();
  const validStage = Math.max(1, Math.min(5, growthStage));
  return `/pets/${normalizedPetType}/${normalizedPetType}-integrated-stage${validStage}.png`;
}

/**
 * Check if an integrated pet image exists by attempting to load it.
 * Returns a promise that resolves to true if the image exists, false otherwise.
 */
export function checkIntegratedImageExists(petType: string, growthStage: number): Promise<boolean> {
  return new Promise((resolve) => {
    const img = new Image();
    const imagePath = getIntegratedPetImagePath(petType, growthStage);
    
    img.onload = () => resolve(true);
    img.onerror = () => resolve(false);
    img.src = imagePath;
  });
}

/**
 * Get background image path (fallback if integrated image doesn't exist).
 * Format: /pets/{petType}/{petType}-background.png
 */
export function getPetBackgroundImagePath(petType: string): string {
  const normalizedPetType = petType.toLowerCase();
  return `/pets/${normalizedPetType}/${normalizedPetType}-background.png`;
}

/**
 * Get Swedish name for a pet type.
 * Returns the Swedish name or the petType itself if not found.
 */
export function getPetNameSwedish(petType: string): string {
  const normalizedPetType = petType.toLowerCase();
  const petNames: Record<string, string> = {
    dragon: "Drake",
    cat: "Katt",
    dog: "Hund",
    bird: "Fågel",
    rabbit: "Kanin",
    bear: "Björn",
    snake: "Orm",
    panda: "Panda",
    slot: "Sengångare",
    hydra: "Hydra",
    unicorn: "Enhörning",
    kapybara: "Kapybara",
  };
  return petNames[normalizedPetType] || petType;
}

/**
 * Get Swedish name for a pet type (lowercase, for use in sentences).
 * Returns the Swedish name in lowercase or the petType itself if not found.
 */
export function getPetNameSwedishLowercase(petType: string): string {
  const normalizedPetType = petType.toLowerCase();
  const petNames: Record<string, string> = {
    dragon: "drake",
    cat: "katt",
    dog: "hund",
    bird: "fågel",
    rabbit: "kanin",
    bear: "björn",
    snake: "orm",
    panda: "panda",
    slot: "sengångare",
    hydra: "hydra",
    unicorn: "enhörning",
    kapybara: "kapybara",
  };
  return petNames[normalizedPetType] || petType;
}
