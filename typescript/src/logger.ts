type Level = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';

const LEVELS: Record<Level, number> = { DEBUG: 0, INFO: 1, WARN: 2, ERROR: 3 };

const activeLevel: Level = ((process.env.LOG_LEVEL ?? 'INFO').toUpperCase() as Level) in LEVELS
  ? ((process.env.LOG_LEVEL ?? 'INFO').toUpperCase() as Level)
  : 'INFO';

function log(level: Level, message: string, ...args: unknown[]): void {
  if (LEVELS[level] < LEVELS[activeLevel]) return;
  const line = `${new Date().toISOString()} [${level}] ${message}`;
  if (level === 'ERROR') console.error(line, ...args);
  else if (level === 'WARN') console.warn(line, ...args);
  else console.log(line, ...args);
}

export const logger = {
  debug: (message: string, ...args: unknown[]) => log('DEBUG', message, ...args),
  info: (message: string, ...args: unknown[]) => log('INFO', message, ...args),
  warn: (message: string, ...args: unknown[]) => log('WARN', message, ...args),
  error: (message: string, ...args: unknown[]) => log('ERROR', message, ...args),
};
