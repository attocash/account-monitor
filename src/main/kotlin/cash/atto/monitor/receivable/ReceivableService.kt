package cash.atto.monitor.receivable

import cash.atto.commons.AttoHash
import cash.atto.commons.AttoReceivable
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
) : CacheSupport {
    private val logger = KotlinLogging.logger {}

    private val scope = CoroutineScope(Dispatchers.Default)

    private val receivableMap = ConcurrentHashMap<AttoHash, AttoReceivable>()

    @EventListener
    fun process(transactionSaved: TransactionSaved) {
        val block = transactionSaved.transaction.block
        if (block !is ReceiveSupport) {
            return
        }
        receivableMap.remove(block.sendHash)
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
                            receivableMap.clear()
                            return@flatMapLatest nodeClient.receivableStream(addresses.toList())
                        }.onStart { logger.info { "Started listening receivables" } }
                        .onCompletion { logger.info { "Stopped listening receivables" } }
                        .collect { receivable ->
                            logger.debug { "Updating $receivable" }
                            receivableMap[receivable.hash] = receivable
                            logger.info { "Updated $receivable" }
                        }
                } catch (e: Exception) {
                    logger.error(e) { "Error while listening receivables stream. Retrying in 10 seconds..." }
                    delay(10.seconds)
                }
            }
        }
    }

    fun getReceivables(): Collection<AttoReceivable> = receivableMap.values

    override fun clear() {
        receivableMap.clear()
    }

    @PreDestroy
    fun close() {
        scope.cancel()
    }
}
