package cash.atto.monitor.transaction

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoSignature
import cash.atto.commons.AttoTransaction
import cash.atto.commons.AttoWork
import kotlinx.io.Buffer
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import java.time.Instant

data class Transaction(
    @Transient
    val block: AttoBlock,
    @Transient
    val signature: AttoSignature,
    @Transient
    val work: AttoWork,
    val receivedAt: Instant = Instant.now(),
    val persistedAt: Instant? = null,
) : Persistable<AttoHash> {
    companion object {}

    @Id
    val hash = block.hash

    @Transient
    val algorithm = block.algorithm

    val publicKey = block.publicKey
    val height = block.height

    val serialized: Buffer
        get() = this.toAttoTransaction().toBuffer()

    override fun getId(): AttoHash = hash

    override fun isNew(): Boolean = true

    fun toAttoTransaction(): AttoTransaction =
        AttoTransaction(
            block = block,
            signature = signature,
            work = work,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Transaction) return false

        if (block != other.block) return false
        if (signature != other.signature) return false
        if (work != other.work) return false
        if (hash != other.hash) return false
        if (algorithm != other.algorithm) return false
        if (publicKey != other.publicKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = block.hashCode()
        result = 31 * result + signature.hashCode()
        result = 31 * result + work.hashCode()
        result = 31 * result + hash.hashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + publicKey.hashCode()
        return result
    }

    override fun toString(): String =
        "Transaction(hash=$hash, publicKey=$publicKey, block=$block, signature=$signature, work=$work, " +
            "receivedAt=$receivedAt, persistedAt=$persistedAt)"
}

fun AttoTransaction.toTransaction(): Transaction =
    Transaction(
        block = block,
        signature = signature,
        work = work,
    )

data class TransactionSaved(
    val transaction: AttoTransaction,
)
