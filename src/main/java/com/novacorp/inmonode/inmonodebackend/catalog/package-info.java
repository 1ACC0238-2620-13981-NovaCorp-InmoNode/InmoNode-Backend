/**
 * Server side of the "Gestión Comercial en Campo" bounded context (schema {@code catalog_management}).
 *
 * <p>The field context runs mostly on the agent's device, offline. On the server it keeps the prospects the agents
 * register and receives the synchronization of their offline records: it stores the prospects and hands each
 * reservation to Control Financiero y Documental, the only authority on lot availability (Customer/Supplier in the
 * Context Map). The package keeps its original name, {@code catalog}.</p>
 */
package com.novacorp.inmonode.inmonodebackend.catalog;
