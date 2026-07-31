# Java Implementation

Java version of the AI-Augmented Accessibility Framework, consumed as a Maven dependency. The sources live at the repository root (`src/`, `pom.xml`). For the framework overview and architecture, see the [root README](../README.md); for the TypeScript version, see [typescript/README.md](../typescript/README.md).

---

## Setup & Installation

### Prerequisites
- Java 21+ (see `pom.xml` for version)
- Maven
- AWS credentials (configured for Bedrock access)
- Playwright browsers if using Playwright (run: `mvn playwright:install`)
- Browser drivers if using Selenium (managed automatically via WebDriverManager)

### Dependencies
Ensure the following are in your `pom.xml`:
- `com.deque.html.axe-core:playwright`
- `org.jsoup:jsoup`
- `org.json:json`
- `org.slf4j:slf4j-simple` (logging)
- `software.amazon.awssdk:bedrockagentruntime` (Bedrock integration)
- `com.microsoft.playwright:playwright`
- `org.apache.logging.log4j:log4j-core` and `log4j-api`
- `com.fasterxml.jackson.core:jackson-databind` and `jackson-datatype-jsr310`
- `org.apache.commons:commons-lang3`
- `org.junit.jupiter:junit-jupiter-api` and `junit-jupiter-engine`

---

## Usage

### Running a Scan
You can scan with either Playwright or Selenium. `AXEScanner.scan(...)` accepts both `Page` and `WebDriver`.

```java
// Playwright example
@Test
void accessibilityAuditPlaywright() {
   page.navigate("https://your-gov-service.gov.uk");
   AXEScanner.scan(page); // Repeat across multiple pages
}

@AfterAll
static void tearDown() {
   AXEScanner.generateFinalReport(); // Generates the cumulative AI report
}
```

```java
// Selenium example
@Test
void accessibilityAuditSelenium() {
   WebDriver driver = new ChromeDriver();
   try {
      driver.get("https://your-gov-service.gov.uk");
      AXEScanner.scan(driver); // Repeat across multiple pages
   } finally {
      driver.quit();
   }
}
```

```java
// Unified driver setup from this project
Object driver = DriverManager.init("playwright", "chrome"); // or ("selenium", "chrome")
AXEScanner.scan(driver);
DriverManager.quit();
```

### Crawling a Whole Service

```java
Object driver = DriverManager.init("playwright", "headless");
SpiderCrawler.crawler(0, "https://your-gov-service.gov.uk", new HashSet<>(), driver);
AXEScanner.generateFinalReport();
DriverManager.quit();
```

By default the crawl is read-only: it only follows links and never submits anything. Pass `true` as the final argument to hand pages containing a form over to the AnswerBot, which fills them and clicks through the journey, scanning every step — this reaches pages behind form submissions that links alone can't:

```java
SpiderCrawler.crawler(0, "https://your-gov-service.gov.uk", new HashSet<>(), driver, true);
```

⚠️ Form filling submits forms against the target service and picks buttons at random, so runs are neither read-only nor reproducible. Only use it against test environments. The crawler and AnswerBot share the visited set, so form-journey pages are not re-scanned by the crawl.

### Configuration
Pass standards via system properties:
```
-Dstandards.scan=wcag22aa,best-practice
```

Create `src/test/resources/application.properties` (or copy from `src/main/resources/application.properties.dist`) and set required values:
```properties
bedrock.region=your_region
bedrock.agent.id=YOUR_BEDROCK_AGENT_ID
bedrock.agent.alias.id=YOUR_BEDROCK_AGENT_ALIAS_ID
```

---

## Report Output
Reports are generated in:
- `target/reports/accessibility-audit-[timestamp].html`

Screenshots are stored in:
- `target/reports/screenshots/`

---

## Troubleshooting
- Ensure AWS credentials are set up for Bedrock access.
- Run `mvn playwright:install` if browsers are missing.
- Check `target/surefire-reports/` for test logs.
