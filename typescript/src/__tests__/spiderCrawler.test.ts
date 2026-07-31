import assert from 'node:assert/strict';
import { test } from 'node:test';
import { normaliseUrl } from '../crawler/spiderCrawler.js';

test('normaliseUrl strips query and fragment', () => {
  assert.equal(
    normaliseUrl('https://Example.gov.uk/Page?foo=bar#section'),
    'https://example.gov.uk/page',
  );
});

test('normaliseUrl strips trailing slash', () => {
  assert.equal(normaliseUrl('https://example.gov.uk/page/'), 'https://example.gov.uk/page');
});

test('normaliseUrl lowercases', () => {
  assert.equal(normaliseUrl('HTTPS://EXAMPLE.GOV.UK/PATH'), 'https://example.gov.uk/path');
});

test('normaliseUrl returns empty string for blank input', () => {
  assert.equal(normaliseUrl(''), '');
  assert.equal(normaliseUrl('   '), '');
  assert.equal(normaliseUrl(null), '');
});

test('normaliseUrl falls back gracefully for non-URL strings', () => {
  assert.equal(normaliseUrl('some-relative-path?q=1#frag'), 'some-relative-path');
});
