package com.hixon.financialApp.model.financialinstitution;

import com.hixon.financialApp.model.register.Transaction;

import java.util.List;

/**
 * What a pending-transactions file holds:  its pending transactions, in file order, and the days the
 * file speaks for.
 *
 * <p>The covered range limits which register rows the pending import may treat as "fallen off the
 * bank's list".  A Wells Fargo file lists everything pending, so its range is unbounded.  A Citi paste
 * covers only the time period it was copied with.  See CITI_PENDING_TRANSACTIONS_DESIGN.md &sect;3.6.</p>
 *
 * @param transactions the pending transactions, in the order the file lists them
 * @param coveredRange the days the file covers
 */
public record ProvisionalFileContents(List<Transaction> transactions, DateRange coveredRange) {
}
