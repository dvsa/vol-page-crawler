# @dvsa/page-crawler (TypeScript)

TypeScript port of the AI-Augmented Accessibility Framework, so Node/TypeScript test suites can consume the same crawler that the Java projects use via Maven. The Java implementation lives at the root of this repository; both versions share the same architecture and behaviour.

An automated testing and auditing engine combining Playwright or Selenium, axe-core, and AWS Bedrock AI to find accessibility violations and provide live GOV.UK Design System (GDS) compliant fixes.

---

## Key Features

- **Intelligent Auto-Fill:** Traverses complex GOV.UK forms using domain-aware logic to reach deep-link pages (`formAutoFill`).
- **Spider Crawler:** Recursively crawls same-domain links and scans every page (`crawler`).
- **Live GDS Scraper:** Dynamically pulls the latest guidance from the GOV.UK Design System to ensure recommendations are always up-to-date.
- **AI Resolution Engine:** Uses AWS Bedrock agents to analyze technical axe violations and rewrite failing HTML into GDS-compliant code.
- **Cumulative Auditing:** Scans multiple pages across a session and merges them into a single, high-fidelity HTML report.
- **Actionable Fixes:** Provides "Download Fix Snippet" buttons for developers to immediately test corrected HTML.

## Architecture

Mirrors the Java framework's four logical layers:

1. **Execution Layer** (`AxeScanner`) — runs axe-core audits via Playwright (`Page`) or Selenium (`WebDriver`), accumulates violations and one full-page screenshot per URL.
2. **Context Layer** (`GovUkScraper`) — cheerio-based engine scrapes GDS Style and Component pages, providing "Ground Truth" context to prevent AI hallucinations.
3. **Intelligence Layer** (`BedrockAgentAnalyser`) — bridges technical errors and human-readable guidance; processes violations in batches via the official AWS SDK (`@aws-sdk/client-bedrock-agent-runtime`).
4. **Presentation Layer** (`generateHtmlReport`) — generates standalone, interactive HTML reports with impact-coded badges and modal screenshot viewers. Fixes sourced via AI → Static Knowledge Base → Hardcoded GDS Fallbacks.

## Installation

The package is published to GitHub Packages, so point the `@dvsa` scope there in your project's `.npmrc`:

```
@dvsa:registry=https://npm.pkg.github.com
//npm.pkg.github.com/:_authToken=${NODE_AUTH_TOKEN}
```

`NODE_AUTH_TOKEN` needs a token with `read:packages` — locally a classic PAT, in GitHub Actions the built-in `GITHUB_TOKEN` (with `packages: read` permission). Then:

```bash
npm install @dvsa/page-crawler
# Playwright browsers, if using the Playwright engine:
npx playwright install
```

Selenium browser drivers are managed automatically by Selenium Manager (built into `selenium-webdriver`).

Requires Node.js 18+.

## Usage

### Running a scan

`AxeScanner.scan(...)` accepts both a Playwright `Page` and a Selenium `WebDriver`.

```ts
// Playwright example
import { AxeScanner } from '@dvsa/page-crawler';

await page.goto('https://your-gov-service.gov.uk');
await AxeScanner.scan(page); // Repeat across multiple pages

// After all scans (e.g. in your test runner's global teardown):
await AxeScanner.generateFinalReport(); // Generates the cumulative AI report
```

```ts
// Selenium example
import { Builder } from 'selenium-webdriver';
import { AxeScanner } from '@dvsa/page-crawler';

const driver = await new Builder().forBrowser('chrome').build();
try {
  await driver.get('https://your-gov-service.gov.uk');
  await AxeScanner.scan(driver); // Repeat across multiple pages
} finally {
  await driver.quit();
}
```

```ts
// Unified driver setup from this package
import { AxeScanner, DriverManager } from '@dvsa/page-crawler';

const driver = await DriverManager.init('playwright', 'chrome'); // or ('selenium', 'chrome')
await AxeScanner.scan(driver);
await DriverManager.quit();
```

### Crawling a whole service

```ts
import { crawler, AxeScanner, DriverManager } from '@dvsa/page-crawler';

const driver = await DriverManager.init('playwright', 'headless');
await crawler(0, 'https://your-gov-service.gov.uk', new Set(), driver);
await AxeScanner.generateFinalReport();
await DriverManager.quit();
```

By default the crawl is read-only: it only follows links and never submits anything. Pass `{ formFill: true }` to hand pages containing a form over to the AnswerBot, which fills them and clicks through the journey, scanning every step — this reaches pages behind form submissions that links alone can't:

```ts
await crawler(0, 'https://your-gov-service.gov.uk', new Set(), driver, { formFill: true });
```

⚠️ `formFill` submits forms against the target service and picks buttons at random, so runs are neither read-only nor reproducible. Only use it against test environments. The crawler and AnswerBot share the visited set, so form-journey pages are not re-scanned by the crawl.

### Auto-filling form journeys

```ts
import { formAutoFill, AxeScanner, DriverManager } from '@dvsa/page-crawler';

const driver = await DriverManager.init('playwright', 'chrome');
await formAutoFill(driver, 'https://your-gov-service.gov.uk/start', 'your-gov-service.gov.uk', true);
await AxeScanner.generateFinalReport();
await DriverManager.quit();
```

## Using in a Test Runner

Realistic, copy-paste examples for the common runners. Two things apply to all of them:

- **Raise the timeout.** A real browser audit takes far longer than the usual 5-second default.
- **Don't parallelise scans.** `AxeScanner` accumulates violations in shared per-process state, so keep all accessibility scans in one test file (or force sequential runs), and call `generateFinalReport()` once at the end.

### Jest

```ts
// tests/accessibility.test.ts
import { Builder } from 'selenium-webdriver';
import { AxeScanner } from '@dvsa/page-crawler';

jest.setTimeout(120_000); // browser audits blow past Jest's 5s default

it('accessibility audit selenium', async () => {
  const driver = await new Builder().forBrowser('chrome').build();
  try {
    await driver.get('https://your-gov-service.gov.uk');
    await AxeScanner.scan(driver); // repeat across multiple pages
  } finally {
    await driver.quit();
  }
});

afterAll(async () => {
  await AxeScanner.generateFinalReport(); // generates the cumulative AI report
});
```

Run it:

```bash
npx jest tests/accessibility.test.ts   # just this file
npm test                               # via the project's test script
```

Jest runs test files in parallel by default — keep the scans in one file, or use `--runInBand`.

### Playwright Test

`AxeScanner.scan(...)` accepts the Playwright `page` fixture directly — no Selenium needed:

```ts
// tests/accessibility.spec.ts
import { test } from '@playwright/test';
import { AxeScanner } from '@dvsa/page-crawler';

test.describe.configure({ mode: 'serial' });
test.setTimeout(120_000);

test('accessibility audit', async ({ page }) => {
  await page.goto('https://your-gov-service.gov.uk');
  await AxeScanner.scan(page); // repeat across multiple pages
});

test.afterAll(async () => {
  await AxeScanner.generateFinalReport();
});
```

Run it:

```bash
npx playwright test tests/accessibility.spec.ts --workers=1
```

### Node built-in test runner

No test framework dependency at all — just `tsx` to run TypeScript directly:

```ts
// tests/accessibility.test.ts
import { test, after } from 'node:test';
import { Builder } from 'selenium-webdriver';
import { AxeScanner } from '@dvsa/page-crawler';

test('accessibility audit selenium', { timeout: 120_000 }, async () => {
  const driver = await new Builder().forBrowser('chrome').build();
  try {
    await driver.get('https://your-gov-service.gov.uk');
    await AxeScanner.scan(driver);
  } finally {
    await driver.quit();
  }
});

after(async () => {
  await AxeScanner.generateFinalReport();
});
```

Run it:

```bash
npm install -D tsx typescript
npx tsx --test tests/accessibility.test.ts
```

Whichever runner you use, the cumulative report lands in `reports/<timestamp>-accessibility.html` once `generateFinalReport()` runs.

## Configuration

The Java version reads JVM system properties and `application.properties`; the TypeScript version reads environment variables and the same `application.properties` format. Environment variables always win, with keys upper-snake-cased (`bedrock.agent.id` → `BEDROCK_AGENT_ID`).

| Setting | Java | TypeScript |
| --- | --- | --- |
| Scan standards | `-Dstandards.scan=wcag22aa,best-practice` | `STANDARDS_SCAN=wcag22aa,best-practice` |
| Crawler path blacklist | `-Dscanner.exclude.paths=/topsreport` | `SCANNER_EXCLUDE_PATHS=/topsreport` |
| Report output directory | `target/reports/` (fixed) | `REPORT_DIR` (default `reports/`) |
| Properties file location | classpath `application.properties` | `PAGE_CRAWLER_CONFIG` path, else `./application.properties`, else `./config/application.properties` |
| Log level | log4j2.xml | `LOG_LEVEL` (DEBUG/INFO/WARN/ERROR) |

`application.properties` keys (same as the Java project):

```properties
bedrock.region=eu-west-2
bedrock.agent.id=YOUR_BEDROCK_AGENT_ID
bedrock.agent.alias.id=YOUR_BEDROCK_AGENT_ALIAS_ID
registration=AB12CDE
vin=SAMPLEVIN012345678
```

AWS credentials resolve through the standard AWS default provider chain (env vars, `~/.aws/credentials`, SSO, instance roles). If no Bedrock agent is configured, `generateFinalReport()` automatically falls back to a basic (non-AI) report.

## Report Output

- Reports: `reports/<timestamp>-accessibility.html`
- Screenshots: `reports/screenshots/`

Override the base directory with `REPORT_DIR`.

## Development

```bash
npm install
npm run build   # compile to dist/
npm test        # compile + run unit tests (node --test)
```

## Troubleshooting

- Ensure AWS credentials are set up for Bedrock access (otherwise the basic report is generated).
- Run `npx playwright install` if Playwright browsers are missing.
- Set `LOG_LEVEL=DEBUG` for verbose crawl/scan logging.

## License

See the LICENSE file at the repository root.
