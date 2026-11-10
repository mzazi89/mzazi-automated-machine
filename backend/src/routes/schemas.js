import { z } from 'zod';

export const loginSchema = z.object({
  email: z.string().email().max(160),
  password: z.string().min(8).max(200)
});

export const refreshSchema = z.object({
  refreshToken: z.string().min(10)
});

export const createTransactionSchema = z.object({
  customerMsisdn: z.string().min(9).max(15),
  network: z.enum(['SAFARICOM', 'AIRTEL', 'TELKOM']),
  bundleId: z.string().uuid(),
  simSlot: z.number().int().min(0).max(3).optional(),
  paymentMethod: z.enum(['MPESA']).optional(),
  testMode: z.boolean().optional()
});

export const simulatePaymentSchema = z.object({
  transactionId: z.string().uuid(),
  outcome: z.enum(['SUCCESS', 'FAILED']).optional()
});

export const bundleSchema = z.object({
  network: z.enum(['SAFARICOM', 'AIRTEL', 'TELKOM']),
  name: z.string().min(1).max(80),
  dataSizeMb: z.number().int().positive().max(1024 * 200),
  sellingPriceCents: z.number().int().positive(),
  costPriceCents: z.number().int().min(0).optional(),
  validityDays: z.number().int().positive().max(365).optional(),
  workflowId: z.string().uuid().nullable().optional(),
  enabled: z.boolean().optional(),
  sortOrder: z.number().int().optional()
});

export const bundleUpdateSchema = bundleSchema.partial();

export const workflowStepSchema = z.object({
  index: z.number().int().min(0),
  label: z.string().min(1).max(80),
  requestTemplate: z.string().min(4).max(64),
  expectedPattern: z.string().max(200).nullable().optional(),
  sendAsSessionInput: z.boolean().optional(),
  timeoutMs: z.number().int().min(5000).max(180000).optional()
});

export const workflowSchema = z.object({
  network: z.enum(['SAFARICOM', 'AIRTEL', 'TELKOM']),
  name: z.string().min(1).max(120),
  steps: z.array(workflowStepSchema).max(10),
  successPattern: z.string().min(2).max(200),
  failurePattern: z.string().max(200).nullable().optional(),
  timeoutMs: z.number().int().min(5000).max(180000).optional(),
  enabled: z.boolean().optional(),
  verifiedBy: z.string().min(2).max(120).optional()
});

export const workflowUpdateSchema = workflowSchema.partial();

export const jobResultSchema = z.object({
  status: z.enum(['SUCCESS', 'FAILED', 'AWAITING_MANUAL_CONFIRMATION', 'CANCELLED', 'DEAD_LETTER']),
  steps: z
    .array(
      z.object({
        index: z.number().int(),
        label: z.string().max(80),
        request: z.string().max(120),
        response: z.string().max(2000).nullable().optional(),
        outcome: z.string().max(60),
        durationMs: z.number().int().min(0)
      })
    )
    .max(20)
    .optional(),
  error: z.string().max(500).nullable().optional(),
  receiptText: z.string().max(2000).nullable().optional(),
  failureCategory: z.string().max(60).nullable().optional(),
  retryable: z.boolean().optional(),
  manualFallback: z.boolean().optional()
});

export const manualConfirmSchema = z.object({
  outcome: z.enum(['SUCCESS', 'FAILED']),
  note: z.string().max(500).nullable().optional(),
  carrierReference: z.string().max(120).nullable().optional()
});

export const deviceCreateSchema = z.object({
  label: z.string().min(2).max(80)
});

export const sellerCreateSchema = z.object({
  email: z.string().email().max(160),
  name: z.string().min(2).max(120),
  password: z.string().min(8).max(200)
});

export const sellerUpdateSchema = z.object({
  name: z.string().min(2).max(120).optional(),
  password: z.string().min(8).max(200).optional(),
  disabled: z.boolean().optional()
});

const shortcode = z.string().regex(/^[0-9]{5,8}$/, 'shortcode must be 5-8 digits');

export const tillCreateSchema = z.object({
  network: z.enum(['SAFARICOM', 'AIRTEL', 'TELKOM']),
  label: z.string().min(2).max(80),
  shortcode,
  accountType: z.enum(['TILL', 'PAYBILL']).optional(),
  accountNumber: z.string().max(40).nullable().optional(),
  passkey: z.string().min(8).max(200).optional(),
  isDefault: z.boolean().optional()
});

export const tillUpdateSchema = z.object({
  label: z.string().min(2).max(80).optional(),
  shortcode: shortcode.optional(),
  accountType: z.enum(['TILL', 'PAYBILL']).optional(),
  accountNumber: z.string().max(40).nullable().optional(),
  passkey: z.string().min(8).max(200).optional(),
  isDefault: z.boolean().optional(),
  enabled: z.boolean().optional()
});
