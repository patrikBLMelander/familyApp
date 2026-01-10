import React from "react";

type EggImageProps = {
  petType: string;
  eggStage: number; // 1-5 for egg stages during hatching animation
  size?: number;
};

/**
 * EggImage component that displays egg images for pet selection and hatching animation.
 * 
 * Expected image structure:
 * - Images should be placed in: /public/pets/{petType}/{petType}-egg-stage{1-5}.png
 * - Example: /public/pets/dragon/dragon-egg-stage1.png, dragon-egg-stage2.png, etc.
 * 
 * Stage 1: Shown when selecting the egg
 * Stages 2-5: Shown during hatching animation (cracking progression)
 */
export function EggImage({ petType, eggStage, size = 160 }: EggImageProps) {
  const validStage = Math.max(1, Math.min(5, eggStage));
  const normalizedPetType = petType.toLowerCase();
  
  // Construct image path
  // Images should be in: /public/pets/{petType}/{petType}-egg-stage{1-5}.png
  const imagePath = `/pets/${normalizedPetType}/${normalizedPetType}-egg-stage${validStage}.png`;
  
  // Fallback if image doesn't exist
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
          fontSize: size * 0.2,
          fontWeight: "bold",
        }}
        title={`${petType} egg stage ${validStage} - Bild saknas`}
      >
        🥚
      </div>
    );
  }

  return (
    <img
      src={imagePath}
      alt={`${petType} egg stage ${validStage}`}
      width={size}
      height={size}
      style={{
        display: "block",
        objectFit: "contain",
        imageRendering: "crisp-edges",
      }}
      onError={handleImageError}
    />
  );
}

