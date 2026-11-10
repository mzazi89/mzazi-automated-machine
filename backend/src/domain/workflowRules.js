/**
 * Server-side mirror of the Android workflow validation.
 * The backend refuses to store a workflow an operator could not legitimately run.
 */

const PLACEHOLDERS = new Set(['msisdn', 'msisdnLocal', 'amount', 'bundleSizeMb', 'bundleCode']);
const DIALABLE = /^[0-9*#+]{4,64}$/;

export function validateUssdTemplate(template) {
  if (typeof template !== 'string' || template.trim() === '') {
    return 'USSD template is empty.';
  }
  const trimmed = template.trim();
  for (const match of trimmed.matchAll(/\{([a-zA-Z0-9_]+)\}/g)) {
    if (!PLACEHOLDERS.has(match[1])) return `Unknown placeholder {${match[1]}}.`;
  }
  // Validate the dialable shape after substituting placeholders: the braces
  // themselves are not valid USSD characters.
  const skeleton = trimmed.replace(/\{[a-zA-Z0-9_]+\}/g, '0');
  if (!DIALABLE.test(skeleton)) {
    return 'Only digits, *, # and + are allowed in a USSD template.';
  }
  if (!trimmed.startsWith('*') && !trimmed.startsWith('#')) {
    return 'A USSD request must start with * or #.';
  }
  if (!trimmed.includes('#')) {
    return 'A USSD request must be terminated with #.';
  }
  return null;
}

export function validateWorkflow({ steps, successPattern, verifiedBy, enabled }) {
  const problems = [];
  if (!Array.isArray(steps) || steps.length === 0) problems.push('Workflow has no steps.');
  if (!successPattern) problems.push('A success receipt pattern is required.');
  if (enabled && !verifiedBy) problems.push('Record who verified these codes with the operator.');

  (steps ?? []).forEach((step, index) => {
    const problem = validateUssdTemplate(step.requestTemplate);
    if (problem) problems.push(`Step ${index + 1}: ${problem}`);
  });
  return problems;
}

export function renderTemplate(template, values) {
  return template.replace(/\{([a-zA-Z0-9_]+)\}/g, (match, name) => {
    const value = values[name];
    return value === undefined ? match : String(value).replace(/[^0-9*#]/g, '');
  });
}
