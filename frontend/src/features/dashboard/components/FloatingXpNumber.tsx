import React, { useEffect, useState } from "react";

type FloatingXpNumberProps = {
  xp: number;
  onComplete?: () => void;
};

/**
 * Floating XP number animation that appears when feeding
 */
export function FloatingXpNumber({ xp, onComplete }: FloatingXpNumberProps) {
  const [isVisible, setIsVisible] = useState(true);

  useEffect(() => {
    const timer = setTimeout(() => {
      setIsVisible(false);
      if (onComplete) {
        setTimeout(onComplete, 300); // Wait for fade out
      }
    }, 1500);

    return () => clearTimeout(timer);
  }, [onComplete]);

  if (!isVisible) return null;

  return (
    <div
      style={{
        position: "absolute",
        top: "50%",
        left: "50%",
        transform: "translate(-50%, -50%)",
        fontSize: "2.5rem",
        fontWeight: 700,
        color: "#48bb78",
        textShadow: "0 2px 4px rgba(0, 0, 0, 0.2)",
        zIndex: 100,
        animation: "floatUp 1.5s ease-out forwards",
        pointerEvents: "none",
      }}
    >
      +{xp} XP
      <style>{`
        @keyframes floatUp {
          0% {
            opacity: 1;
            transform: translate(-50%, -50%) scale(0.8);
          }
          50% {
            opacity: 1;
            transform: translate(-50%, -150%) scale(1.2);
          }
          100% {
            opacity: 0;
            transform: translate(-50%, -200%) scale(1);
          }
        }
      `}</style>
    </div>
  );
}
