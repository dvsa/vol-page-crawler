import type { Result } from 'axe-core';
import type { BedrockRecommendation } from '../ai/bedrockRecommendation.js';
import type { ViolationEntry } from './axeScanner.js';

/** Port of org.dvsa.testing.framework.axe.HtmlReportGenerator. */

function escapeHtml(text: string | null | undefined): string {
  if (!text) return '';
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#x27;');
}

function truncateText(text: string | null | undefined): string {
  if (!text) return '';
  if (text.length <= 150) return text;
  return `${text.slice(0, 150)}...`;
}

const IMPACT_COLORS: Record<string, string> = {
  critical: '#d4351c',
  serious: '#f47738',
  minor: '#1d70b8',
  moderate: '#4c2c92',
};

function getManualGdsFallback(ruleId: string): string {
  switch (ruleId) {
    case 'page-has-heading-one':
      return "<h1 class='govuk-heading-xl'>Page Title</h1>";
    case 'landmark-one-main':
    case 'landmark-unique':
      return "<main class='govuk-main-wrapper' id='main-content' role='main'>\n  \n</main>";
    case 'label':
      return "<div class='govuk-form-group'>\n  <label class='govuk-label' for='input-id'>Label</label>\n  <input class='govuk-input' id='input-id' type='text'>\n</div>";
    case 'color-contrast':
      return "<button class='govuk-button'>Save and continue</button>";
    default:
      return '';
  }
}

function getScreenshotHtml(filename: string | undefined, ruleId: string): string {
  if (!filename) {
    return `<em style='color: #6f777b; font-size: 11px;'>No screenshot for ${ruleId}</em>`;
  }
  let cleanName = filename.includes('/') ? filename.slice(filename.lastIndexOf('/') + 1) : filename;
  if (!cleanName.toLowerCase().endsWith('.png')) cleanName += '.png';
  return (
    `<img src='screenshots/${cleanName}' class='screenshot-img' onclick='openModal(this.src)' ` +
    `onerror="this.style.display='none'; this.parentElement.innerHTML='Image Error';" alt='Screenshot' />`
  );
}

export function generateHtmlReport(
  violations: ViolationEntry[],
  bedrockRecommendationMap: Map<string, BedrockRecommendation>,
  kbMap: Map<string, BedrockRecommendation>,
  pageScreenshots: Map<string, string>,
): string {
  const uniqueRuleCount = new Set(violations.map((entry) => entry.rule.id)).size;
  const pageCount = new Set(violations.map((entry) => entry.pageUrl)).size;

  const parts: string[] = [];

  parts.push(
    '<!DOCTYPE html>\n',
    "<html><head><meta charset='UTF-8'><title>Accessibility Report</title>",
    '<style>',
    "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 20px; line-height: 1.5; color: #333; }",
    'h1 { color: #0b0c0c; }',
    'table { width: 100%; border-collapse: collapse; margin-bottom: 40px; table-layout: fixed; }',
    'th, td { border: 1px solid #bfc1c3; padding: 12px; text-align: left; vertical-align: top; word-wrap: break-word; }',
    'th { background-color: #f3f2f1; font-weight: bold; }',
    '.screenshot-img { max-width: 100%; height: auto; cursor: pointer; border: 1px solid #ddd; max-height: 150px; transition: 0.3s; }',
    '.screenshot-img:hover { opacity: 0.7; }',
    '.impact-badge { padding: 4px 8px; font-weight: bold; color: white; border-radius: 3px; text-transform: uppercase; font-size: 11px; }',
    '.ai-fix-box { background-color: #f8f8f8; border-left: 4px solid #005ea5; padding: 10px; margin-top: 10px; }',
    "code { font-family: 'Courier New', monospace; background: #eef; padding: 2px 4px; display: block; white-space: pre-wrap; margin-top: 5px; border: 1px solid #ccc;}",
    '.download-btn { display: inline-block; background-color: #00823b; color: white; padding: 8px 12px; text-decoration: none; border-radius: 3px; font-size: 12px; margin-top: 10px; font-weight: bold; }',
    '.download-btn:hover { background-color: #005a27; }',
    '.modal { display: none; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.9); overflow: auto; }',
    '.modal-content { margin: auto; display: block; width: 80%; max-width: 1200px; padding: 40px 0; }',
    '.summary { background: #f3f2f1; padding: 15px; margin-bottom: 20px; border-left: 4px solid #1d70b8; }',
    '</style>',
    '<script>',
    "function openModal(src) { document.getElementById('screenshotModal').style.display='block'; document.getElementById('modalImage').src=src; }",
    "window.onclick = function(event) { if (event.target == document.getElementById('screenshotModal')) { document.getElementById('screenshotModal').style.display = 'none'; } }",
    '</script>',
    '</head><body>',
    '<h1>Accessibility Report (AI Augmented)</h1>',
    "<div class='summary'>",
    `<strong>Total violation instances:</strong> ${violations.length}`,
    ` | <strong>Unique rules:</strong> ${uniqueRuleCount}`,
    ` | <strong>Pages scanned:</strong> ${pageCount}`,
    '</div>',
    '<table><tr>',
    "<th style='width: 12%;'>Source URL</th>",
    "<th style='width: 18%;'>Screenshot</th>",
    "<th style='width: 10%;'>Rule ID</th>",
    "<th style='width: 15%;'>Failing HTML</th>",
    "<th style='width: 8%;'>Impact</th>",
    "<th style='width: 37%;'>AI Audit & Fix (GOV.UK Scraped)</th>",
    '</tr>',
  );

  for (const entry of violations) {
    const rule: Result = entry.rule;
    const pageUrl = entry.pageUrl;

    const impact = rule.impact ?? 'unknown';
    const impactColor = IMPACT_COLORS[impact] ?? '#505a5f';

    let br: BedrockRecommendation | undefined =
      bedrockRecommendationMap.get(rule.id) ?? kbMap.get(rule.id);

    if (!br || !br.example) {
      br = {
        ruleId: rule.id,
        issue: rule.description,
        recommendation: br?.recommendation ?? 'Follow GOV.UK Design System patterns.',
        example: getManualGdsFallback(rule.id),
        reference: 'https://design-system.service.gov.uk/',
      };
    }

    for (const node of rule.nodes) {
      let rawFilename = pageScreenshots.get(pageUrl);

      if (!rawFilename && pageUrl) {
        const target = pageUrl.replace(/\/$/, '').toLowerCase();
        for (const [url, filename] of pageScreenshots) {
          if (url.replace(/\/$/, '').toLowerCase() === target) {
            rawFilename = filename;
            break;
          }
        }
      }

      const screenshotHtml = getScreenshotHtml(rawFilename, rule.id);

      parts.push(
        '<tr>',
        `<td><a href='${pageUrl}' target='_blank' style='font-size: 11px;'>${pageUrl}</a></td>`,
        `<td>${screenshotHtml}</td>`,
        `<td><strong>${rule.id}</strong></td>`,
        `<td><code>${escapeHtml(truncateText(node.html))}</code></td>`,
        `<td><span class='impact-badge' style='background-color:${impactColor};'>${impact}</span></td>`,
        '<td>',
        `<strong>Issue:</strong> ${escapeHtml(br.issue)}<br><br>`,
        `<strong>Guidance:</strong> ${escapeHtml(br.recommendation)}<br>`,
      );

      if (br.example) {
        const encodedFix = encodeURIComponent(br.example);
        parts.push(
          "<div class='ai-fix-box'>",
          '<strong>Suggested Fix:</strong>',
          `<code>${escapeHtml(br.example)}</code>`,
          `<a href='data:text/html;charset=utf-8,${encodedFix}' download='${rule.id}-fix.html' class='download-btn'>`,
          'Download Fix Snippet</a>',
          '</div>',
        );
      }

      if (br.reference) {
        parts.push(
          `<br><small><strong>Source:</strong> <a href='${br.reference}' target='_blank'>GOV.UK Docs</a></small>`,
        );
      }
      parts.push('</td></tr>');
    }
  }

  parts.push(
    '</table>',
    '<div id=\'screenshotModal\' class=\'modal\' onclick=\'this.style.display="none"\'>',
    "<img class='modal-content' id='modalImage'>",
    '</div>',
    '</body></html>',
  );

  return parts.join('');
}
