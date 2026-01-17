import { PetImage } from "./PetImage";

type PetVisualizationProps = {
  petType: string;
  growthStage: number; // 1-5
  size?: "small" | "medium" | "large";
};

// Base sizes in pixels for different display sizes
const BASE_SIZES = {
  small: 60,
  medium: 120,
  large: 300, // Increased to show full pet
};

export function PetVisualization({ petType, growthStage, size = "medium" }: PetVisualizationProps) {
  // Ensure growth stage is within valid range (1-5)
  const validStage = Math.max(1, Math.min(5, growthStage));
  const baseSize = BASE_SIZES[size];

  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        filter: "drop-shadow(0 4px 8px rgba(0, 0, 0, 0.15))",
        transition: "all 0.4s ease",
        animation: validStage >= 3 ? "gentle-bounce 3s ease-in-out infinite" : validStage === 2 ? "tiny-bounce 4s ease-in-out infinite" : "none",
        transform: validStage === 1 ? "scale(0.95)" : "scale(1)",
      }}
    >
      <PetImage petType={petType} growthStage={validStage} size={baseSize} />
      <style>{`
        @keyframes gentle-bounce {
          0%, 100% { transform: translateY(0) scale(1); }
          50% { transform: translateY(-6px) scale(1.02); }
        }
        @keyframes tiny-bounce {
          0%, 100% { transform: translateY(0) scale(1); }
          50% { transform: translateY(-3px) scale(1.01); }
        }
      `}</style>
    </div>
  );
}
