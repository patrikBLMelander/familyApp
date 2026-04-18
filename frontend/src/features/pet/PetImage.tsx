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

  // Primary path: transparent stage image (no background)
  // Fallback path: integrated image (also transparent for most pets)
  const stagePath = `/pets/${normalizedPetType}/${normalizedPetType}-stage${validStage}.png`;
  const integratedPath = `/pets/${normalizedPetType}/${normalizedPetType}-integrated-stage${validStage}.png`;

  const [imageSrc, setImageSrc] = React.useState(stagePath);
  const [imageError, setImageError] = React.useState(false);

  React.useEffect(() => {
    setImageSrc(stagePath);
    setImageError(false);
  }, [stagePath]);

  const handleImageError = () => {
    if (imageSrc === stagePath) {
      // Stage image missing — try the integrated image
      setImageSrc(integratedPath);
    } else {
      setImageError(true);
    }
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
      src={imageSrc}
      alt={`${petType} at growth stage ${validStage}`}
      style={{
        display: "block",
        width: `${size}px`,
        height: `${size}px`,
        objectFit: "contain",
        objectPosition: "center",
      }}
      onError={handleImageError}
    />
  );
}

