export function normaliseUrl(urlString: string | null | undefined): string {
  if (!urlString || !urlString.trim()) return '';

  try {
    const uri = new URL(urlString.trim());
    let normalized = `${uri.protocol}//${uri.host}${uri.pathname || '/'}`.toLowerCase();

    if (normalized.endsWith('/') && normalized.length > 8) {
      normalized = normalized.slice(0, -1);
    }
    return normalized;
  } catch {
    let fallback = urlString.trim().toLowerCase();
    if (fallback.includes('#')) fallback = fallback.split('#')[0];
    if (fallback.includes('?')) fallback = fallback.split('?')[0];
    if (fallback.endsWith('/') && fallback.length > 8) {
      fallback = fallback.slice(0, -1);
    }
    return fallback;
  }
}
