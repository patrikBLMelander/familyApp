/**
 * Utility functions for consistent error handling across calendar components.
 */

/**
 * Extracts a user-friendly error message from an error object.
 * Handles Error instances, string errors, and unknown error types.
 * 
 * @param error - The error to extract message from
 * @param defaultMessage - Default message if error cannot be extracted (default: "Ett oväntat fel uppstod.")
 * @returns User-friendly error message in Swedish
 */
export function extractErrorMessage(
  error: unknown,
  defaultMessage: string = "Ett oväntat fel uppstod."
): string {
  if (error instanceof Error) {
    return error.message;
  }
  if (typeof error === "string") {
    return error;
  }
  return defaultMessage;
}
