/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.vcard4android

import android.content.*
import android.os.RemoteException
import android.os.TransactionTooLargeException
import java.util.*

class BatchOperation(
        private val providerClient: ContentProviderClient
) {

    private val queue = LinkedList<Operation>()
    private lateinit var results: Array<ContentProviderResult?>


    fun nextBackrefIdx() = queue.size

    fun enqueue(operation: Operation) = queue.add(operation)

    fun commit(): Int {
        var affected = 0
        if (!queue.isEmpty())
            try {
                Constants.log.fine("Committing ${queue.size} operations …")

                results = Array(queue.size, { null })
                runBatch(0, queue.size)

                for (result in results.filterNotNull())
                    when {
                        result.count != null -> affected += result.count
                        result.uri != null   -> affected += 1
                    }
                Constants.log.fine("… $affected record(s) affected")

            } catch(e: Exception) {
                throw ContactsStorageException("Couldn't apply batch operation", e)
            }

        queue.clear()
        return affected
    }

    fun getResult(idx: Int) = results[idx]


    /**
     * Runs a subset of the operations in [queue] using [providerClient] in a transaction.
     * Catches [TransactionTooLargeException] and splits the operations accordingly.
     * @param start index of first operation which will be run (inclusive)
     * @param end   index of last operation which will be run (exclusive!)
     * @throws RemoteException on contact provider errors
     * @throws OperationApplicationException when the batch can't be processed
     * @throws ContactsStorageException if the transaction is too large or if the batch operation failed partially
     */
    private fun runBatch(start: Int, end: Int) {
        if (end == start)
            return     // nothing to do

        try {
            Constants.log.fine("Running operations $start to ${end-1}")
            val partResults = providerClient.applyBatch(toCPO(start, end))

            val n = end - start
            if (partResults.size != n)
                throw ContactsStorageException("Batch operation failed partially (only ${partResults.size} of $n operations done)")

            System.arraycopy(partResults, 0, results, start, n)
        } catch(e: TransactionTooLargeException) {
            if (end <= start + 1)
            // only one operation, can't be split
                throw ContactsStorageException("Can't transfer data to content provider (data row too large)")

            Constants.log.warning("Transaction too large, splitting (losing atomicity)")
            val mid = start + (end - start)/2
            runBatch(start, mid)
            runBatch(mid, end)
        }
    }

    private fun toCPO(start: Int, end: Int): ArrayList<ContentProviderOperation> {
        val cpo = ArrayList<ContentProviderOperation>(end - start)

        for ((i, op) in queue.subList(start, end).withIndex()) {
            val builder = op.builder
            op.backrefKey?.let { key ->
                if (op.backrefIdx < start)
                // back reference is outside of the current batch
                    results[op.backrefIdx]?.let { result ->
                        builder.withValueBackReferences(null)
                                .withValue(key, ContentUris.parseId(result.uri))
                    }
                else
                // back reference is in current batch, apply offset
                    builder.withValueBackReference(key, op.backrefIdx - start)
            }

            // set a yield point at least every 300 operations
            if (i % 300 == 0)
                builder.withYieldAllowed(true)

            cpo += builder.build()
        }
        return cpo
    }


    class Operation constructor(
            val builder: ContentProviderOperation.Builder,
            val backrefKey: String? = null,
            val backrefIdx: Int = -1
    )

}
