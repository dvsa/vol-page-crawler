import { logger } from '../logger.js';

/** Port of org.dvsa.testing.framework.utils.DomainValidator. */
export class DomainValidator {
  static isSameDomain(urlStr: string, baseDomain: string, allowSubdomains: boolean): boolean {
    try {
      const host = new URL(urlStr).hostname?.toLowerCase();
      if (!host) return false;

      const base = baseDomain.toLowerCase();
      return host === base || (allowSubdomains && host.includes(base));
    } catch {
      return false;
    }
  }

  static extractDomain(url: string | null | undefined): string | null {
    if (!url) return null;
    try {
      return new URL(url).hostname;
    } catch {
      logger.warn(`Malformed URL: ${url}`);
      return null;
    }
  }
}
