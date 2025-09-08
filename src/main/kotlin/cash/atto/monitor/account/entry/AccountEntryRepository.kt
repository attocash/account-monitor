package cash.atto.monitor.account.entry

import cash.atto.commons.AttoHash
import cash.atto.commons.AttoPublicKey
import cash.atto.monitor.transaction.PublicKeyHeightView
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.time.Instant

interface AccountEntryRepository : CoroutineCrudRepository<AccountEntry, AttoHash> {
    @Query(
        """
        SELECT algorithm,
               public_key,
               MAX(height) AS height
        FROM account_entry
        WHERE public_key IN (:publicKeys)
        GROUP BY algorithm, public_key
        """,
    )
    suspend fun findLatestHeights(publicKeys: List<AttoPublicKey>): List<PublicKeyHeightView>

    @Query(
        """
        SELECT *
        FROM account_entry
        WHERE persisted_at BETWEEN :persistedAtFrom AND :persistedAtTo
        ORDER BY persisted_at
        """,
    )
    fun findAllByPersistedAtBetween(
        persistedAtFrom: Instant,
        persistedAtTo: Instant,
    ): Flow<AccountEntry>
}
