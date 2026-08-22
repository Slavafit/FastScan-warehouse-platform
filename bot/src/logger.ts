type LogLevel = 'silent' | 'error' | 'warn' | 'info' | 'debug';

const levelPriority: Record<LogLevel, number> = {
  silent: 0,
  error: 1,
  warn: 2,
  info: 3,
  debug: 4,
};

function readLogLevel(): LogLevel {
  const value = process.env.BOT_LOG_LEVEL?.trim().toLowerCase();
  if (value === 'silent' || value === 'error' || value === 'warn' || value === 'info' || value === 'debug') {
    return value;
  }
  return 'info';
}

const currentLevel = readLogLevel();

function shouldLog(level: LogLevel): boolean {
  return levelPriority[currentLevel] >= levelPriority[level];
}

export const logger = {
  debug: (...args: unknown[]) => {
    if (shouldLog('debug')) console.log(...args);
  },
  info: (...args: unknown[]) => {
    if (shouldLog('info')) console.log(...args);
  },
  warn: (...args: unknown[]) => {
    if (shouldLog('warn')) console.warn(...args);
  },
  error: (...args: unknown[]) => {
    if (shouldLog('error')) console.error(...args);
  },
};
