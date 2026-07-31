/** Port of org.dvsa.testing.framework.ai.BedrockRecommendation (Java record). */
export interface BedrockRecommendation {
  ruleId?: string;
  recommendation?: string;
  reference?: string;
  example?: string;
  issue?: string;
  /** Raw text returned by the model when it could not be parsed into fields. */
  text?: string;
}
