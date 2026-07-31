package org.dvsa.testing.framework.ai;

import com.deque.html.axecore.results.Rule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dvsa.testing.framework.jsoup.GovUkScraper;
import org.dvsa.testing.framework.utils.AccessibilityMapper;
import org.json.JSONArray;
import org.json.JSONObject;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BedrockAgentAnalyser {
    private static final Logger LOGGER = LogManager.getLogger(BedrockAgentAnalyser.class);
    private final GovUkScraper scraper = new GovUkScraper();
    private final HttpClient client = HttpClient.newHttpClient();

    private final String bedrockRegion;
    private final String agentId;
    private final String agentAliasId;

    public BedrockAgentAnalyser() {
        this.bedrockRegion = org.dvsa.testing.framework.config.AppConfig.getString("bedrock.region", "eu-west-2");
        this.agentId = org.dvsa.testing.framework.config.AppConfig.getString("bedrock.agent.id");
        this.agentAliasId = org.dvsa.testing.framework.config.AppConfig.getString("bedrock.agent.alias.id");
    }

    public Map<String, BedrockRecommendation> analyseUniqueViolations(Map<String, Rule> uniqueRules) {
        LOGGER.info("Starting chunked analysis for {} unique rule IDs", uniqueRules.size());

        Map<String, BedrockRecommendation> finalMap = new HashMap<>();

        ConcurrentHashMap<Rule, String> tempMap = new ConcurrentHashMap<>();
        for (Map.Entry<String, Rule> entry : uniqueRules.entrySet()) {
            tempMap.put(entry.getValue(), entry.getKey());
        }
        String liveContext = buildScrapedContext(tempMap);

        List<Map.Entry<String, Rule>> ruleList = new ArrayList<>(uniqueRules.entrySet());
        int chunkSize = 3;

        for (int i = 0; i < ruleList.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, ruleList.size());
            List<Map.Entry<String, Rule>> chunk = ruleList.subList(i, end);

            LOGGER.info("Processing chunk {}/{} (Rules {}-{})",
                    (i / chunkSize) + 1, (int) Math.ceil((double) ruleList.size() / chunkSize), i + 1, end);

            String chunkJson = formatUniqueRulesChunkAsJson(chunk);
            String finalPrompt = String.format(
                    """
                            SYSTEM: You are a GOV.UK Accessibility Auditor. You must fix violations using GOV.UK Design System patterns.
                            USER: Fix these violations.\s
                            
                            MANDATORY OUTPUT RULES:
                            1. Every item MUST have an 'example' field containing a GDS HTML snippet (e.g. using 'govuk-' classes).
                            2. If the GDS context doesn't mention the specific rule, use your general knowledge of GOV.UK components (Buttons, Inputs, Headings) to provide the fix.
                            3. Return ONLY a valid JSON array.
                            
                            ### GDS CONTEXT:
                            %s
                            
                            ### VIOLATIONS:
                            %s
                            """,
                    liveContext, chunkJson
            );

            try {
                String rawResponse = invokeAgentViaRest(new JSONObject().put("inputText", finalPrompt));
                List<BedrockRecommendation> recs = parseResponse(rawResponse);

                for (int j = 0; j < recs.size(); j++) {
                    if (j >= chunk.size()) break;

                    String ruleId = chunk.get(j).getKey();
                    BedrockRecommendation rec = recs.get(j);

                    finalMap.put(ruleId, BedrockRecommendation.builder()
                            .ruleId(ruleId)
                            .issue(rec.issue())
                            .recommendation(rec.recommendation())
                            .reference(rec.reference())
                            .example(rec.example())
                            .build());
                }

                if (end < ruleList.size()) {
                    LOGGER.info("Chunk complete. Sleeping for 1500ms to prevent rate limiting...");
                    Thread.sleep(1500);
                }

            } catch (Exception e) {
                LOGGER.error("Error processing rule chunk starting at index {}: {}", i, e.getMessage());
            }
        }

        return finalMap;
    }

    private String formatUniqueRulesChunkAsJson(List<Map.Entry<String, Rule>> chunk) {
        JSONArray array = new JSONArray();
        for (Map.Entry<String, Rule> entry : chunk) {
            JSONObject obj = new JSONObject();
            obj.put("ruleId", entry.getKey());
            obj.put("description", entry.getValue().getDescription());
            obj.put("impact", entry.getValue().getImpact());
            array.put(obj);
        }
        return array.toString();
    }

    private List<BedrockRecommendation> parseResponse(String rawResponse) {
        if (!rawResponse.trim().startsWith("[") && !rawResponse.trim().startsWith("{")) {
            return fallbackRegexParse(rawResponse);
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(rawResponse, new TypeReference<>() {
            });
        } catch (Exception e) {
            return fallbackRegexParse(rawResponse);
        }
    }

    private List<BedrockRecommendation> fallbackRegexParse(String text) {
        List<BedrockRecommendation> recs = new ArrayList<>();

        Pattern plainTextPattern = Pattern.compile(
                "(?i)Issue:\\s*(.*?)\\s*Recommendation:\\s*(.*?)\\s*Reference:\\s*(.*?)\\s*Example:\\s*(.*?)(?=Issue:|$)",
                Pattern.DOTALL
        );

        Matcher m = plainTextPattern.matcher(text);

        while (m.find()) {
            recs.add(BedrockRecommendation.builder()
                    .issue(m.group(1).trim())
                    .recommendation(m.group(2).trim())
                    .reference(m.group(3).trim())
                    .example(m.group(4).trim())
                    .build());
        }

        if (recs.isEmpty()) {
            Pattern jsonPattern = Pattern.compile("\"issue\":\\s*\"(.*?)\",\\s*\"recommendation\":\\s*\"(.*?)\"", Pattern.DOTALL);
            Matcher m2 = jsonPattern.matcher(text);
            while (m2.find()) {
                recs.add(BedrockRecommendation.builder().issue(m2.group(1)).recommendation(m2.group(2)).build());
            }
        }

        return recs;
    }

    private String buildScrapedContext(ConcurrentHashMap<Rule, String> violations) {
        return violations.keySet().stream()
                .flatMap(rule -> AccessibilityMapper.mapAxeRuleToGovPaths(rule.getId()).stream())
                .distinct()
                .map(path -> "### " + path.toUpperCase() + "\n" + scraper.getLiveGuidance(path))
                .collect(Collectors.joining("\n\n"));
    }

    private String invokeAgentViaRest(JSONObject promptJson) throws Exception {
        String sessionId = "session-" + UUID.randomUUID();
        String endpoint = String.format(
                "https://bedrock-agent-runtime.%s.amazonaws.com/agents/%s/agentAliases/%s/sessions/%s/text",
                bedrockRegion, agentId, agentAliasId, sessionId
        );

        SdkHttpFullRequest sdkRequest = SdkHttpFullRequest.builder()
                .method(SdkHttpMethod.POST)
                .uri(URI.create(endpoint))
                .appendHeader("Content-Type", "application/json")
                .contentStreamProvider(() -> new ByteArrayInputStream(promptJson.toString().getBytes(StandardCharsets.UTF_8)))
                .build();

        Aws4Signer signer = Aws4Signer.create();
        SdkHttpFullRequest signed = signer.sign(sdkRequest, Aws4SignerParams.builder()
                .signingRegion(Region.of(bedrockRegion))
                .signingName("bedrock")
                .awsCredentials(DefaultCredentialsProvider.create().resolveCredentials())
                .build());

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .POST(HttpRequest.BodyPublishers.ofString(promptJson.toString()));

        List<String> restricted = List.of("host", "content-length", "transfer-encoding", "expect", "connection");
        signed.headers().forEach((k, v) -> {
            if (!restricted.contains(k.toLowerCase())) {
                v.forEach(val -> builder.header(k, val));
            }
        });

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new RuntimeException("Bedrock call failed [" + response.statusCode() + "]: " + response.body());
        }

        return decodeEventStreamResponse(response.body().getBytes(StandardCharsets.UTF_8));
    }

    private String decodeEventStreamResponse(byte[] responseBytes) {
        String raw = new String(responseBytes, StandardCharsets.UTF_8);
        Pattern base64Pattern = Pattern.compile("\"bytes\"\\s*:\\s*\"([A-Za-z0-9+/=]+)\"");
        Matcher matcher = base64Pattern.matcher(raw);

        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            sb.append(new String(Base64.getDecoder().decode(matcher.group(1)), StandardCharsets.UTF_8));
        }
        return !sb.isEmpty() ? sb.toString() : raw;
    }
}