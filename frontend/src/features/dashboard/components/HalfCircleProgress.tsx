import React from "react";

type HalfCircleProgressProps = {
  progress: number; // 0-100
  currentLevel: number;
  mood?: "happy" | "hungry";
  petName?: string;
  size?: number; // Size in pixels (width/height of the circle)
  strokeWidth?: number;
};

/**
 * Full circle (360 degrees) progress bar component
 * Positioned so only the upper half (180 degrees) is visible over the image
 */
export function HalfCircleProgress({
  progress,
  currentLevel,
  mood = "happy",
  petName,
  size = 120,
  strokeWidth = 8,
}: HalfCircleProgressProps) {
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
      height: size, // Show full circle
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
        {/* Background circle (full 360 degrees) */}
        <circle
          cx={centerX}
          cy={centerY}
          r={radius}
          fill="none"
          stroke="#e2e8f0"
          strokeWidth={strokeWidth}
        />
        {/* Progress circle (full 360 degrees, fills progressively) */}
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
      {/* Content in center: mood icon, name, and level */}
      <div style={{
        position: "absolute",
        top: "50%",
        left: "50%",
        transform: "translate(-50%, -50%)",
        zIndex: 1,
        background: "rgba(255, 255, 255, 0.95)",
        borderRadius: "50%",
        width: size * 0.7,
        height: size * 0.7,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        boxShadow: "0 2px 8px rgba(0, 0, 0, 0.1)",
        padding: "8px",
      }}>
        {/* Mood icon */}
        <div style={{
          fontSize: "1.5rem",
          marginBottom: "4px",
        }}>
          {mood === "happy" ? "😊" : "🥺"}
        </div>
        {/* Pet name */}
        {petName && (
          <div style={{
            fontSize: "0.75rem",
            fontWeight: 700,
            color: "#2d3748",
            textAlign: "center",
            lineHeight: "1.2",
            marginBottom: "2px",
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
            maxWidth: "100%",
          }}>
            {petName}
          </div>
        )}
        {/* Level */}
        <div style={{
          fontSize: "0.7rem",
          fontWeight: 700,
          color: "#2d3748",
          textAlign: "center",
        }}>
          {currentLevel}
        </div>
      </div>
    </div>
  );
}
