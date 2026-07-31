import * as fs from 'node:fs';
import * as path from 'node:path';

/**
 * Port of org.dvsa.testing.framework.config.AppConfig.
 *
 * Loads key=value pairs from an application.properties file. Lookup order:
 *   1. Environment variable (key transformed: bedrock.agent.id -> BEDROCK_AGENT_ID)
 *   2. Properties file (path from PAGE_CRAWLER_CONFIG, else ./application.properties,
 *      else ./config/application.properties)
 *   3. Supplied default
 */
export class AppConfig {
  private static properties: Map<string, string> | null = null;

  private static load(): Map<string, string> {
    if (this.properties) return this.properties;

    const candidates = [
      process.env.PAGE_CRAWLER_CONFIG,
      path.resolve('application.properties'),
      path.resolve('config', 'application.properties'),
    ].filter((p): p is string => !!p);

    this.properties = new Map();
    for (const candidate of candidates) {
      if (!fs.existsSync(candidate)) continue;
      const content = fs.readFileSync(candidate, 'utf-8');
      for (const rawLine of content.split(/\r?\n/)) {
        const line = rawLine.trim();
        if (!line || line.startsWith('#') || line.startsWith('!')) continue;
        const separator = line.search(/[=:]/);
        if (separator <= 0) continue;
        const key = line.slice(0, separator).trim();
        const value = line.slice(separator + 1).trim();
        this.properties.set(key, value);
      }
      break;
    }
    return this.properties;
  }

  private static envKey(key: string): string {
    return key.replace(/[.\-]/g, '_').toUpperCase();
  }

  static getString(key: string): string | undefined;
  static getString(key: string, defaultValue: string): string;
  static getString(key: string, defaultValue?: string): string | undefined {
    return process.env[this.envKey(key)] ?? this.load().get(key) ?? defaultValue;
  }

  static getBaseUrls(): string[] {
    const rawUrls = this.getString('baseURLs');
    if (!rawUrls || !rawUrls.trim()) return [];
    return rawUrls
      .split(',')
      .map((url) => url.trim())
      .filter((url) => url.length > 0);
  }

  static getBoolean(key: string, defaultValue: boolean): boolean {
    const value = this.getString(key);
    return value !== undefined ? value.toLowerCase() === 'true' : defaultValue;
  }

  static getInt(key: string, defaultValue: number): number {
    const value = this.getString(key);
    if (value !== undefined) {
      const parsed = Number.parseInt(value, 10);
      return Number.isNaN(parsed) ? defaultValue : parsed;
    }
    return defaultValue;
  }
}
