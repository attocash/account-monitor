package cash.atto.monitor.receivable

import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoUnit
import cash.atto.commons.toAttoVersion
import cash.atto.monitor.receivable.ReceivableController.SortWith
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class ReceivableControllerTest {
    private val twenty = receivable(20, 40, 1)
    private val thirty = receivable(30, 10, 1)
    private val ten = receivable(10, 20, 1)
    private val forty = receivable(40, 30, 2)
    private val service = mockk<ReceivableService>()
    private val controller = ReceivableController(service)

    init {
        every { service.getReceivables() } returns listOf(twenty, thirty, ten, forty).asSequence()
    }

    @ParameterizedTest
    @EnumSource(SortWith::class)
    fun `limit selects the first entries in the requested order`(order: SortWith) {
        val expected =
            when (order) {
                SortWith.AMOUNT_ASC -> listOf(ten, twenty)
                SortWith.AMOUNT_DESC -> listOf(forty, thirty)
                SortWith.TIME_ASC -> listOf(thirty, ten)
                SortWith.TIME_DESC -> listOf(twenty, forty)
            }
        assertEquals(expected, controller.getAll(limit = 2, minAmount = null, sortedWith = order))
    }

    @Test
    fun `hash address and amount filters apply before the limit`() {
        val actual =
            controller.getAll(
                limit = 1,
                hashes = setOf(ten.hash, twenty.hash, forty.hash),
                addresses = setOf(twenty.receiverAddress),
                minAmount = twenty.amount,
            )
        assertEquals(listOf(twenty), actual)
    }

    private fun receivable(
        amount: Int,
        time: Long,
        receiver: Int,
    ): AttoReceivable =
        AttoReceivable(
            version = 0U.toAttoVersion(),
            algorithm = AttoAlgorithm.V1,
            publicKey = AttoPublicKey(ByteArray(32)),
            receiverAlgorithm = AttoAlgorithm.V1,
            receiverPublicKey = AttoPublicKey(ByteArray(32) { receiver.toByte() }),
            amount = AttoAmount.from(AttoUnit.ATTO, amount.toString()),
            timestamp = Instant.fromEpochMilliseconds(time),
            hash = AttoHash(ByteArray(32) { amount.toByte() }),
        )
}
