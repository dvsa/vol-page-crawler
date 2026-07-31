## AI-Augmented Accessibility Framework

An automated testing and auditing engine combining Selenium or Playwright, Axe-core, and AWS Bedrock AI to find accessibility violations and provide live GOV.UK Design System (GDS) compliant fixes.

---

### Repository Layout

The framework ships in two languages so both Java and TypeScript projects can consume it:

| Implementation | Location | Consumed via | Documentation |
| --- | --- | --- | --- |
| Java | repository root (`src/`, `pom.xml`) | Maven dependency | [src/README.md](src/README.md) |
| TypeScript | [`typescript/`](typescript/) | npm package `@dvsa/page-crawler` | [typescript/README.md](typescript/README.md) |

Both implementations share the same architecture and behaviour. When adding a feature or fixing a bug in one, mirror the change in the other so the two stay in sync.

---

### Key Features
- **Intelligent Auto-Fill:** Traverses complex GOV.UK forms using domain-aware logic to reach deep-link pages.
- **Spider Crawler:** Recursively crawls same-domain links and scans every page.
- **Live GDS Scraper:** Dynamically pulls the latest guidance from the GOV.UK Design System to ensure recommendations are always up-to-date.
- **AI Resolution Engine:** Uses AWS Bedrock to analyze technical Axe violations and rewrite failing HTML into GDS-compliant code.
- **Cumulative Auditing:** Scans multiple pages across a session and merges them into a single, high-fidelity HTML report.
- **Actionable Fixes:** Provides "Download Fix Snippet" buttons for developers to immediately test corrected HTML.

---

### Architecture
Both implementations are divided into the same four logical layers:

1. **Execution Layer** (AXE scanner)
   - Uses either Playwright (`Page`) or Selenium (`WebDriver`) to run Axe-core audits.
   - State Management: Accumulates violations and screenshots across the session.
   - Screenshot Mapping: Captures one full-page screenshot per URL for visual evidence.

2. **Context Layer** (GOV.UK scraper)
   - HTML-parsing engine (JSoup in Java, cheerio in TypeScript) scrapes GDS Style and Component pages, providing "Ground Truth" context to prevent AI hallucinations.

3. **Intelligence Layer** (Bedrock agent analyser)
   - Bridges technical errors and human-readable guidance.
   - Processes violations in batches for AI accuracy and robust API communication.

4. **Presentation Layer** (HTML report generator)
   - Generates standalone, interactive HTML reports.
   - Fixes sourced via AI → Static Knowledge Base → Hardcoded GDS Fallbacks.
   - Visual Audit: Impact-coded badges and modal screenshot viewers.

---

### Getting Started

- Java projects: see [src/README.md](src/README.md) for Maven setup, usage, and configuration.
- TypeScript/Node projects: see [typescript/README.md](typescript/README.md) for npm installation, test-runner examples, and configuration.

---

### License
See LICENSE file for details.
