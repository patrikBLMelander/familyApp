import React, { useEffect, useState } from "react";

type ConfettiAnimationProps = {
  onComplete?: () => void;
  duration?: number;
};

/**
 * Confetti celebration animation for level ups
 */
export function ConfettiAnimation({ onComplete, duration = 3000 }: ConfettiAnimationProps) {
  const [isActive, setIsActive] = useState(true);

  useEffect(() => {
    const timer = setTimeout(() => {
      setIsActive(false);
      if (onComplete) {
        onComplete();
      }
    }, duration);

    return () => clearTimeout(timer);
  }, [duration, onComplete]);

  if (!isActive) return null;

  // Create confetti particles
  const confettiParticles = Array.from({ length: 50 }, (_, i) => ({
    id: i,
    left: Math.random() * 100,
    delay: Math.random() * 0.5,
    duration: 2 + Math.random() * 2,
    color: ["#ff6b6b", "#4ecdc4", "#45b7d1", "#f9ca24", "#f0932b", "#eb4d4b", "#6c5ce7"][
      Math.floor(Math.random() * 7)
    ],
  }));

  return (
    <div
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        pointerEvents: "none",
        zIndex: 9999,
        overflow: "hidden",
      }}
    >
      {confettiParticles.map((particle) => (
        <div
          key={particle.id}
          style={{
            position: "absolute",
            left: `${particle.left}%`,
            top: "-10px",
            width: "10px",
            height: "10px",
            backgroundColor: particle.color,
            borderRadius: "50%",
            animation: `confettiFall ${particle.duration}s ease-in ${particle.delay}s forwards`,
          }}
        />
      ))}
      <style>{`
        @keyframes confettiFall {
          0% {
            transform: translateY(0) rotate(0deg);
            opacity: 1;
          }
          100% {
            transform: translateY(100vh) rotate(720deg);
            opacity: 0;
          }
        }
      `}</style>
    </div>
  );
}
