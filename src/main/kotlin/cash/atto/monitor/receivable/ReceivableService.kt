package cash.atto.monitor.receivable

import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoUnit
import cash.atto.commons.ReceiveSupport
import cash.atto.commons.node.AttoNodeOperations
import cash.atto.monitor.CacheSupport
import cash.atto.monitor.account.AccountService
import cash.atto.monitor.transaction.TransactionSaved
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import mu.KotlinLogging
import org.springframework.context.annotation.DependsOn
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

@Service
@DependsOn("flywayInitializer")
class ReceivableService(
    private val accountService: AccountService,
    private val nodeClient: AttoNodeOperations,
    private val properties: ReceivableProperties,
) : CacheSupport {
    private val logger = KotlinLogging.logger {}

    private val scope = CoroutineScope(Dispatchers.Default)

    private val receivableStateMap = ConcurrentHashMap<AttoHash, ReceivableState>()

    @EventListener
    fun process(transactionSaved: TransactionSaved) {
        val block = transactionSaved.transaction.block
        if (block !is ReceiveSupport) {
            return
        }
        receivableStateMap.compute(block.sendHash) { _, state ->
            when (state) {
                is ReceivableState.Pending -> null
                else -> ReceivableState.Received(block.sendHash, Clock.System.now())
            }
        }
    }

    @PostConstruct
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startStream() {
        scope.launch {
            while (isActive) {
                try {
                    accountService
                        .streamActiveAddressUpdates()
                        .flatMapLatest { addresses ->
                            if (addresses.isEmpty()) {
                                return@flatMapLatest emptyFlow()
                            }
                            return@flatMapLatest nodeClient.receivableStream(addresses.toList())
                        }.onStart { logger.info { "Started listening receivables" } }
                        .onCompletion { logger.info { "Stopped listening receivables" } }
                        .collect { receivable ->
                            logger.debug { "Updating $receivable" }
                            receivableStateMap.compute(receivable.hash) { _, state ->
                                when (state) {
                                    is ReceivableState.Received -> null
                                    else -> ReceivableState.Pending(receivable)
                                }
                            }
                            logger.info { "Updated $receivable" }
                        }
                } catch (e: Exception) {
                    logger.error(e) { "Error while listening receivables stream. Retrying in 10 seconds..." }
                    delay(10.seconds)
                }
            }
        }
    }

    fun getReceivables(): Collection<AttoReceivable> {
        val minAmount = AttoAmount.from(AttoUnit.ATTO, properties.minAmount)
        return receivableStateMap.values
            .filterIsInstance<ReceivableState.Pending>()
            .map { it.receivable }
            .filter { it.amount >= minAmount }
            .sortedByDescending { it.amount }
    }

    override fun clear() {
        receivableStateMap.clear()
    }

    @PreDestroy
    fun close() {
        scope.cancel()
    }

    private sealed interface ReceivableState {
        val hash: AttoHash

        data class Received(override val hash: AttoHash, val instant: Instant) : ReceivableState
        data class Pending(val receivable: AttoReceivable) : ReceivableState {
            override val hash: AttoHash
                get() = receivable.hash
        }
    }
}
