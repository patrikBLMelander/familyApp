import { CalendarTaskWithCompletionResponse } from "../../../shared/api/calendar";

/**
 * Sorts tasks by required status first (required tasks first),
 * then alphabetically by title within each group.
 */
export function sortTasksByRequiredAndTitle<T extends { event: { isRequired: boolean; title: string } }>(
  tasks: T[]
): T[] {
  return [...tasks].sort((a, b) => {
    // Required tasks first
    if (a.event.isRequired !== b.event.isRequired) {
      return a.event.isRequired ? -1 : 1;
    }
    // Then alphabetically by title
    return a.event.title.localeCompare(b.event.title);
  });
}
