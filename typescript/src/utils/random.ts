import { randomInt } from 'node:crypto';

const ALPHA = 'abcdefghijklmnopqrstuvwxyz';
const NUMERIC = '0123456789';
const ALPHANUMERIC = ALPHA + ALPHA.toUpperCase() + NUMERIC;

function randomFrom(chars: string, length: number): string {
  let result = '';
  for (let i = 0; i < length; i++) {
    result += chars[randomInt(chars.length)];
  }
  return result;
}

/** Equivalents of the Apache Commons RandomStringUtils helpers used by the Java AnswerBot. */
export const randomAlphabetic = (length: number): string => randomFrom(ALPHA, length);
export const randomNumeric = (length: number): string => randomFrom(NUMERIC, length);
export const randomAlphanumeric = (length: number): string => randomFrom(ALPHANUMERIC, length);

/** Random integer in [minInclusive, maxExclusive), like ThreadLocalRandom.nextInt/nextLong. */
export const randomIntBetween = (minInclusive: number, maxExclusive: number): number =>
  randomInt(minInclusive, maxExclusive);
