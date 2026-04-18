/**
 * Food utilities for pets - maps pet types to their favorite food emojis
 */

export function getPetFoodEmoji(petType: string): string {
  const normalizedPetType = petType.toLowerCase();
  const foodMap: Record<string, string> = {
    dragon: "🔥", // Fire berries / gems
    cat: "🐟", // Fish
    dog: "🦴", // Bones
    bird: "🌾", // Seeds
    rabbit: "🥕", // Carrots
    bear: "🍯", // Honey
    snake: "🥚", // Eggs
    panda: "🎋", // Bamboo
    slot: "🍃", // Leaves
    hydra: "💧", // Water drops
    unicorn: "✨", // Star fruits
    kapybara: "🌿", // Grass
    shark: "🐠", // Fish
    lion: "🥩", // Meat
  };
  return foodMap[normalizedPetType] || "🍎"; // Default to apple
}

export function getPetFoodName(petType: string): string {
  const normalizedPetType = petType.toLowerCase();
  const foodNameMap: Record<string, string> = {
    dragon: "eldbär",
    cat: "fisk",
    dog: "ben",
    bird: "frön",
    rabbit: "morötter",
    bear: "honung",
    snake: "ägg",
    panda: "bambu",
    slot: "löv",
    hydra: "vattendroppar",
    unicorn: "stjärnfrukter",
    kapybara: "gräs",
    shark: "fiskar",
    lion: "kött",
  };
  return foodNameMap[normalizedPetType] || "mat";
}

/**
 * Pet mood messages - 15 random messages for happy and hungry states
 */
export const PET_MOOD_MESSAGES = {
  happy: [
    "Jag är så glad! 🎉",
    "Tack för maten! Det var gott! 😊",
    "Jag känner mig stark nu! 💪",
    "Mmm, så gott! Jag vill ha mer! 🍽️",
    "Du är bäst! Tack för att du tar hand om mig! ❤️",
    "Jag älskar dig! Du ger mig så mycket energi! ⚡",
    "Wow, jag växer så mycket tack vare dig! 🌱",
    "Jag är så nöjd! Fortsätt så! 👏",
    "Det här är det bästa! Jag är så mätt! 😋",
    "Tack! Jag känner mig så pigg nu! 🎈",
    "Du gör mig så glad! Fortsätt ge mig mat! 🎊",
    "Jag älskar när du ger mig mat! Det är så roligt! 🎁",
    "Tack! Nu kan jag växa stort och starkt! 🌟",
    "Jag är så tacksam! Du är min bästa vän! 🤗",
    "Det här smakar så gott! Jag vill ha mer snart! 🍰",
  ],
  hungry: [
    "Jag är hungrig... Kan jag få lite mat? 🥺",
    "Snälla, ge mig något att äta... Jag är så hungrig! 😢",
    "Jag behöver mat för att växa... Var snäll? 🙏",
    "Jag känner mig svag utan mat... Kan du hjälpa mig? 💔",
    "Jag längtar efter mat... När får jag äta? 😔",
    "Jag är så hungrig... Snälla ge mig något! 🍽️",
    "Jag behöver energi... Kan du ge mig mat? ⚡",
    "Jag är hungrig och ledsen... Var snäll och ge mig mat? 😞",
    "Jag kan inte växa utan mat... Hjälp mig? 🌱",
    "Jag är så hungrig... När kommer maten? 🕐",
    "Jag behöver mat för att må bra... Snälla? 💙",
    "Jag är hungrig och trött... Kan jag få lite mat? 😴",
    "Jag längtar efter att få mat... Var snäll? 🥺",
    "Jag är så hungrig... Kommer du att ge mig mat snart? ⏰",
    "Jag behöver mat för att vara glad igen... Hjälp mig? 💛",
  ],
};

export function getRandomPetMessage(mood: "happy" | "hungry"): string {
  const messages = PET_MOOD_MESSAGES[mood];
  return messages[Math.floor(Math.random() * messages.length)];
}
