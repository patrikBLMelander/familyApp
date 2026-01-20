import type { CSSProperties } from "react";

/**
 * Utility functions for consistent button styling across calendar components.
 */

/**
 * Returns style object for toggle buttons (used in CalendarViewSelector and CalendarFilters).
 * 
 * @param isActive - Whether the button is in active/pressed state
 * @returns Style object for the toggle button
 */
export function getToggleButtonStyle(isActive: boolean): CSSProperties {
  return {
    flex: 1,
    padding: "8px 12px",
    borderRadius: "6px",
    border: "none",
    background: isActive ? "#b8e6b8" : "transparent",
    color: isActive ? "#2d5a2d" : "#6b6b6b",
    fontWeight: isActive ? 600 : 400,
    fontSize: "0.85rem",
    cursor: "pointer",
    transition: "all 0.2s ease",
  };
}

/**
 * Returns style object for toggle button container.
 * 
 * @returns Style object for the container div
 */
export function getToggleButtonContainerStyle(): CSSProperties {
  return {
    display: "flex",
    gap: "4px",
    padding: "4px",
    background: "rgba(255, 255, 255, 0.6)",
    borderRadius: "8px",
    border: "1px solid rgba(220, 210, 200, 0.3)",
  };
}

/**
 * Returns style object for member filter button container.
 * 
 * @returns Style object for the member filter container div
 */
export function getMemberFilterContainerStyle(): CSSProperties {
  return {
    display: "flex",
    gap: "4px",
    padding: "4px",
    background: "#f5f5f5",
    borderRadius: "8px",
    marginBottom: "12px",
  };
}
