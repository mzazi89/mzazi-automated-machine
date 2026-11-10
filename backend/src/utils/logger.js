const LEVELS = { debug: 10, info: 20, warn: 30, error: 40 };

const configured = LEVELS[process.env.LOG_LEVEL] ?? LEVELS.info;

function emit(level, message, fields = {}) {
  if (LEVELS[level] < configured) return;
  const line = {
    ts: new Date().toISOString(),
    level,
    message,
    ...fields
  };
  // Single-line JSON keeps logs parseable by hosted platforms.
  const output = JSON.stringify(line);
  if (level === 'error') console.error(output);
  else console.log(output);
}

export const logger = {
  debug: (message, fields) => emit('debug', message, fields),
  info: (message, fields) => emit('info', message, fields),
  warn: (message, fields) => emit('warn', message, fields),
  error: (message, fields) => emit('error', message, fields)
};
