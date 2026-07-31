import { randomUUID } from 'node:crypto';
import {
  BedrockAgentRuntimeClient,
  InvokeAgentCommand,
} from '@aws-sdk/client-bedrock-agent-runtime';
import type { Result } from 'axe-core';
import { AppConfig } from '../config/appConfig.js';
import { logger } from '../logger.js';
import { GovUkScraper } from '../scraper/govUkScraper.js';
import { AccessibilityMapper } from '../utils/accessibilityMapper.js';
import type { BedrockRecommendation } from './bedrockRecommendation.js';

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Port of org.dvsa.testing.framework.ai.BedrockAgentAnalyser.
 *
 * The Java version hand-signs REST calls with Aws4Signer and decodes the event
 * stream manually; here the official AWS SDK (InvokeAgentCommand) handles both.
 * Credentials resolve through the standard AWS default provider chain.
 */
export class BedrockAgentAnalyser {
  private readonly scraper = new GovUkScraper();
  private readonly client: BedrockAgentRuntimeClient;
  private readonly agentId: string | undefined;
  private readonly agentAliasId: string | undefined;

  constructor() {
    const region = AppConfig.getString('bedrock.region', 'eu-west-2');
    this.agentId = AppConfig.getString('bedrock.agent.id');
    this.agentAliasId = AppConfig.getString('bedrock.agent.alias.id');
    this.client = new BedrockAgentRuntimeClient({ region });
  }

  async analyseUniqueViolations(
    uniqueRules: Map<string, Result>,
  ): Promise<Map<string, BedrockRecommendation>> {
    logger.info(`Starting chunked analysis for ${uniqueRules.size} unique rule IDs`);

    const finalMap = new Map<string, BedrockRecommendation>();
    const liveContext = await this.buildScrapedContext([...uniqueRules.values()]);

    const ruleList = [...uniqueRules.entries()];
    const chunkSize = 3;

    for (let i = 0; i < ruleList.length; i += chunkSize) {
      const end = Math.min(i + chunkSize, ruleList.length);
      const chunk = ruleList.slice(i, end);

      logger.info(
        `Processing chunk ${Math.floor(i / chunkSize) + 1}/${Math.ceil(ruleList.length / chunkSize)} (Rules ${i + 1}-${end})`,
      );

      const chunkJson = JSON.stringify(
        chunk.map(([ruleId, rule]) => ({
          ruleId,
          description: rule.description,
          impact: rule.impact ?? undefined,
        })),
      );

      const finalPrompt = this.buildPrompt(liveContext, chunkJson);

      try {
        const rawResponse = await this.invokeAgent(finalPrompt);
        const recs = this.parseResponse(rawResponse);

        for (let j = 0; j < recs.length && j < chunk.length; j++) {
          const [ruleId] = chunk[j];
          const rec = recs[j];

          finalMap.set(ruleId, {
            ruleId,
            issue: rec.issue,
            recommendation: rec.recommendation,
            reference: rec.reference,
            example: rec.example,
          });
        }

        if (end < ruleList.length) {
          logger.info('Chunk complete. Sleeping for 1500ms to prevent rate limiting...');
          await sleep(1500);
        }
      } catch (e) {
        logger.error(
          `Error processing rule chunk starting at index ${i}: ${e instanceof Error ? e.message : e}`,
        );
      }
    }

    return finalMap;
  }

  private buildPrompt(liveContext: string, chunkJson: string): string {
    return `SYSTEM: You are a GOV.UK Accessibility Auditor. You must fix violations using GOV.UK Design System patterns.
USER: Fix these violations.

MANDATORY OUTPUT RULES:
1. Every item MUST have an 'example' field containing a GDS HTML snippet (e.g. using 'govuk-' classes).
2. If the GDS context doesn't mention the specific rule, use your general knowledge of GOV.UK components (Buttons, Inputs, Headings) to provide the fix.
3. Return ONLY a valid JSON array.

### GDS CONTEXT:
${liveContext}

### VIOLATIONS:
${chunkJson}
`;
  }

  private parseResponse(rawResponse: string): BedrockRecommendation[] {
    const trimmed = rawResponse.trim();
    if (!trimmed.startsWith('[') && !trimmed.startsWith('{')) {
      return this.fallbackRegexParse(rawResponse);
    }

    try {
      const parsed: unknown = JSON.parse(trimmed);
      if (Array.isArray(parsed)) {
        return parsed as BedrockRecommendation[];
      }
      return [parsed as BedrockRecommendation];
    } catch {
      return this.fallbackRegexParse(rawResponse);
    }
  }

  private fallbackRegexParse(text: string): BedrockRecommendation[] {
    const recs: BedrockRecommendation[] = [];

    const plainTextPattern =
      /Issue:\s*([\s\S]*?)\s*Recommendation:\s*([\s\S]*?)\s*Reference:\s*([\s\S]*?)\s*Example:\s*([\s\S]*?)(?=Issue:|$)/gi;

    for (const match of text.matchAll(plainTextPattern)) {
      recs.push({
        issue: match[1].trim(),
        recommendation: match[2].trim(),
        reference: match[3].trim(),
        example: match[4].trim(),
      });
    }

    if (recs.length === 0) {
      const jsonPattern = /"issue":\s*"([\s\S]*?)",\s*"recommendation":\s*"([\s\S]*?)"/g;
      for (const match of text.matchAll(jsonPattern)) {
        recs.push({ issue: match[1], recommendation: match[2] });
      }
    }

    return recs;
  }

  private async buildScrapedContext(rules: Result[]): Promise<string> {
    const paths = [
      ...new Set(rules.flatMap((rule) => AccessibilityMapper.mapAxeRuleToGovPaths(rule.id))),
    ];

    const sections: string[] = [];
    for (const path of paths) {
      const guidance = await this.scraper.getLiveGuidance(path);
      sections.push(`### ${path.toUpperCase()}\n${guidance}`);
    }
    return sections.join('\n\n');
  }

  private async invokeAgent(inputText: string): Promise<string> {
    if (!this.agentId || !this.agentAliasId) {
      throw new Error(
        'Bedrock agent not configured: set bedrock.agent.id and bedrock.agent.alias.id in application.properties (or BEDROCK_AGENT_ID / BEDROCK_AGENT_ALIAS_ID env vars).',
      );
    }

    const response = await this.client.send(
      new InvokeAgentCommand({
        agentId: this.agentId,
        agentAliasId: this.agentAliasId,
        sessionId: `session-${randomUUID()}`,
        inputText,
      }),
    );

    let completion = '';
    if (response.completion) {
      const decoder = new TextDecoder();
      for await (const event of response.completion) {
        if (event.chunk?.bytes) {
          completion += decoder.decode(event.chunk.bytes);
        }
      }
    }
    return completion;
  }
}
