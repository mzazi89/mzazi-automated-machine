import { createAuthService } from './authService.js';
import { createCatalogueService } from './catalogueService.js';
import { createDarajaClient } from './darajaClient.js';
import { createPaymentService } from './paymentService.js';
import { createTransactionService } from './transactionService.js';
import { createUssdService } from './ussdService.js';
import { createAuditService } from './auditService.js';

/**
 * Wires the service layer. Order matters: the payment service needs the USSD queue,
 * and transactions need payments — the dependency graph is acyclic and explicit.
 */
export function createServices({ repo, config, fetchImpl }) {
  const audit = createAuditService({ repo });
  const daraja = createDarajaClient({ config, fetchImpl });
  const ussd = createUssdService({ repo, audit });
  const payments = createPaymentService({ repo, config, daraja, ussd, audit });
  const transactions = createTransactionService({ repo, config, payments, audit });
  const catalogue = createCatalogueService({ repo, audit });
  const auth = createAuthService({ repo, config, audit });

  return { audit, daraja, ussd, payments, transactions, catalogue, auth };
}
