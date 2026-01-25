import React from "react";

type CircleProgressProps = {
  progress: number; // 0-100
  currentLevel: number;
  size?: number; // Size in pixels (width/height of the circle)
  strokeWidth?: number;
};

/**
 * Full circle (360 degrees) progress bar component
 */
export function CircleProgress({
  progress,
  currentLevel,
  size = 120,
  strokeWidth = 8,
}: CircleProgressProps) {
  const radius = (size - strokeWidth) / 2;
  const centerX = size / 2;
  const centerY = size / 2;
  
  // Calculate circumference for full circle
  const circumference = 2 * Math.PI * radius;
  
  // Calculate stroke-dasharray and stroke-dashoffset for progress
  // Start from top (12 o'clock) and go clockwise
  const offset = circumference - (progress / 100) * circumference;
  
  // Unique gradient ID to avoid conflicts
  const gradientId = `progressGradient-${currentLevel}`;
  
  return (
    <div style={{
      position: "relative",
      width: size,
      height: size,
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
    }}>
      <svg
        width={size}
        height={size}
        style={{
          transform: "rotate(-90deg)", // Start from top
        }}
        viewBox={`0 0 ${size} ${size}`}
      >
        <defs>
          <linearGradient id={gradientId} x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="#667eea" />
            <stop offset="100%" stopColor="#764ba2" />
          </linearGradient>
        </defs>
        {/* Background circle */}
        <circle
          cx={centerX}
          cy={centerY}
          r={radius}
          fill="none"
          stroke="#e2e8f0"
          strokeWidth={strokeWidth}
        />
        {/* Progress circle */}
        <circle
          cx={centerX}
          cy={centerY}
          r={radius}
          fill="none"
          stroke={`url(#${gradientId})`}
          strokeWidth={strokeWidth}
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          strokeLinecap="round"
          style={{
            transition: "stroke-dashoffset 0.5s ease",
          }}
        />
      </svg>
      {/* Level number in center */}
      <div style={{
        position: "absolute",
        top: "50%",
        left: "50%",
        transform: "translate(-50%, -50%)",
        fontSize: "1.2rem",
        fontWeight: 700,
        color: "#2d3748",
        zIndex: 1,
        background: "rgba(255, 255, 255, 0.9)",
        borderRadius: "50%",
        width: "40px",
        height: "40px",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
      }}>
        {currentLevel}
      </div>
    </div>
  );
}
