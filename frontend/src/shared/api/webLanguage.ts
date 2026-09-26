import { API_BASE_URL } from "../config";

/**
 * The web UI is Swedish only, but the backend now localizes its messages from
 * Accept-Language -- and a browser set to English would get English errors inside a
 * Swedish page. So every API call from the web says "sv", in one wrapper around fetch
 * (same approach as paymentRequiredNotice: shared/api has no single request helper).
 *
 * When the web gets real translations, this should send the chosen language instead.
 */
const WEB_LANGUAGE = "sv";

function isApiRequest(input: RequestInfo | URL): boolean {
  const url = typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
  return url.startsWith(API_BASE_URL);
}

/** Call once, at start-up. Safe to call twice; the second call does nothing. */
export function installWebLanguageHeader() {
  const w = window as Window & { __kidquestLanguageHeaderInstalled?: boolean };
  if (w.__kidquestLanguageHeaderInstalled) {
    return;
  }
  w.__kidquestLanguageHeaderInstalled = true;

  const original = window.fetch.bind(window);
  window.fetch = (input: RequestInfo | URL, init?: RequestInit) => {
    if (!isApiRequest(input)) {
      return original(input, init);
    }
    const headers = new Headers(init?.headers ?? (input instanceof Request ? input.headers : undefined));
    headers.set("Accept-Language", WEB_LANGUAGE);
    return original(input, { ...init, headers });
  };
}
