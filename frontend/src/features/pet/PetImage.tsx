import React from "react";

type PetImageProps = {
  petType: string;
  growthStage: number; // 1-5
  size?: number; // Size in pixels
};

/**
 * PetImage component that displays pet images based on type and growth stage.
 * 
 * Expected image structure:
 * - Images should be placed in: /public/pets/{petType}/{petType}-stage{1-5}.png
 * - Example: /public/pets/dragon/dragon-stage1.png, dragon-stage2.png, etc.
 * 
 * Supported pet types: dragon, cat, dog, bird, rabbit
 */
export function PetImage({ petType, growthStage, size = 100 }: PetImageProps) {
  const validStage = Math.max(1, Math.min(5, growthStage));
  const normalizedPetType = petType.toLowerCase();
  
  // Construct image path
  // Images should be in: /public/pets/{petType}/{petType}-stage{1-5}.png
  const imagePath = `/pets/${normalizedPetType}/${normalizedPetType}-stage${validStage}.png`;
  
  // Fallback if image doesn't exist - show a placeholder
  const [imageError, setImageError] = React.useState(false);
  
  const handleImageError = () => {
    setImageError(true);
  };

  if (imageError) {
    // Fallback placeholder if image is missing
    return (
      <div
        style={{
          width: size,
          height: size,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: "#E0E0E0",
          borderRadius: "50%",
          color: "#9E9E9E",
          fontSize: size * 0.3,
          fontWeight: "bold",
        }}
        title={`${petType} stage ${validStage} - Bild saknas`}
      >
        {petType.charAt(0).toUpperCase()}
        {validStage}
      </div>
    );
  }

  return (
    <img
      src={imagePath}
      alt={`${petType} at growth stage ${validStage}`}
      width={size}
      height={size}
      style={{
        display: "block",
        objectFit: "contain",
        imageRendering: "crisp-edges", // Keep sharp edges for pixel art style if needed
      }}
      onError={handleImageError}
    />
  );
}

