import assert from 'node:assert/strict';
import { test } from 'node:test';
import { DomainValidator } from '../utils/domainValidator.js';

test('isSameDomain matches exact host', () => {
  assert.equal(
    DomainValidator.isSameDomain('https://example.gov.uk/page', 'example.gov.uk', false),
    true,
  );
});

test('isSameDomain rejects different host', () => {
  assert.equal(
    DomainValidator.isSameDomain('https://other.gov.uk/page', 'example.gov.uk', false),
    false,
  );
});

test('isSameDomain rejects subdomain when subdomains not allowed', () => {
  assert.equal(
    DomainValidator.isSameDomain('https://sub.example.gov.uk/page', 'example.gov.uk', false),
    false,
  );
});

test('isSameDomain accepts subdomain when subdomains allowed', () => {
  assert.equal(
    DomainValidator.isSameDomain('https://sub.example.gov.uk/page', 'example.gov.uk', true),
    true,
  );
});

test('isSameDomain returns false for malformed URLs', () => {
  assert.equal(DomainValidator.isSameDomain('not a url', 'example.gov.uk', false), false);
});

test('extractDomain returns hostname', () => {
  assert.equal(DomainValidator.extractDomain('https://example.gov.uk/some/path'), 'example.gov.uk');
});

test('extractDomain returns null for malformed URL', () => {
  assert.equal(DomainValidator.extractDomain('not a url'), null);
});
