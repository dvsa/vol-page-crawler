import assert from 'node:assert/strict';
import { test } from 'node:test';
import type { Result } from 'axe-core';
import { generateHtmlReport } from '../axe/htmlReportGenerator.js';
import type { ViolationEntry } from '../axe/axeScanner.js';

function fakeRule(overrides: Partial<Result> = {}): Result {
  return {
    id: 'color-contrast',
    description: 'Elements must have sufficient color contrast',
    help: 'Fix contrast',
    helpUrl: 'https://dequeuniversity.com/rules/axe/color-contrast',
    impact: 'serious',
    tags: ['wcag2aa'],
    nodes: [
      {
        html: '<button class="low-contrast">Go</button>',
        impact: 'serious',
        target: ['button'],
        any: [],
        all: [],
        none: [],
      },
    ],
    ...overrides,
  } as Result;
}

test('generateHtmlReport renders violations with escaping and fallback fix', () => {
  const violations: ViolationEntry[] = [
    { rule: fakeRule(), pageUrl: 'https://example.gov.uk/start' },
  ];

  const html = generateHtmlReport(violations, new Map(), new Map(), new Map());

  assert.ok(html.startsWith('<!DOCTYPE html>'));
  assert.ok(html.includes('color-contrast'));
  assert.ok(html.includes('https://example.gov.uk/start'));
  // failing HTML must be escaped
  assert.ok(html.includes('&lt;button class=&quot;low-contrast&quot;&gt;Go&lt;/button&gt;'));
  // color-contrast has a hardcoded GDS fallback fix
  assert.ok(html.includes('govuk-button'));
  assert.ok(html.includes('Download Fix Snippet'));
});

test('generateHtmlReport prefers AI recommendation when present', () => {
  const violations: ViolationEntry[] = [
    { rule: fakeRule(), pageUrl: 'https://example.gov.uk/start' },
  ];
  const recommendations = new Map([
    [
      'color-contrast',
      {
        ruleId: 'color-contrast',
        issue: 'Low contrast button',
        recommendation: 'Use the standard green govuk-button.',
        example: "<button class='govuk-button'>Continue</button>",
        reference: 'https://design-system.service.gov.uk/components/button/',
      },
    ],
  ]);

  const html = generateHtmlReport(violations, recommendations, new Map(), new Map());

  assert.ok(html.includes('Use the standard green govuk-button.'));
  assert.ok(html.includes('components/button'));
});
