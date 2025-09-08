package cash.atto.monitor.transaction

import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoPublicKey
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.time.Instant

interface TransactionRepository : CoroutineCrudRepository<Transaction, AttoHash> {
    @Query(
        """
        SELECT algorithm,
               public_key,
               MAX(height) AS height
        FROM transaction
        WHERE public_key IN (:publicKeys)
        GROUP BY algorithm, public_key
        """,
    )
    suspend fun findLatestHeights(publicKeys: List<AttoPublicKey>): List<PublicKeyHeightView>

    @Query(
        """
        SELECT *
        FROM transaction
        WHERE persisted_at BETWEEN :persistedAtFrom AND :persistedAtTo
        ORDER BY persisted_at
        """,
    )
    fun findAllByPersistedAtBetween(
        persistedAtFrom: Instant,
        persistedAtTo: Instant,
    ): Flow<Transaction>
}

data class PublicKeyHeightView(
    val algorithm: AttoAlgorithm,
    val publicKey: AttoPublicKey,
    val height: AttoHeight,
)
