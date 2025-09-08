package cash.atto.monitor.transaction

import cash.atto.commons.AttoAddress
import cash.atto.commons.node.AccountHeightSearch
import cash.atto.commons.node.AttoNodeOperations
import cash.atto.commons.node.HeightSearch
import cash.atto.commons.toAttoHeight
import cash.atto.monitor.account.AccountService
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
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Service
import kotlin.time.Duration.Companion.seconds

@Service
@DependsOn("flywayInitializer")
class TransactionService(
    private val accountService: AccountService,
    private val repository: TransactionRepository,
    private val nodeClient: AttoNodeOperations,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val logger = KotlinLogging.logger {}

    private val scope = CoroutineScope(Dispatchers.Default)

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

                            val latestHeights = repository.findLatestHeights(addresses.map { it.publicKey })

                            val knownAddresses = latestHeights.map { AttoAddress(it.algorithm, it.publicKey) }.toSet()

                            val unknownHeights =
                                addresses
                                    .filter { !knownAddresses.contains(AttoAddress(it.algorithm, it.publicKey)) }
                                    .map {
                                        PublicKeyHeightView(it.algorithm, it.publicKey, 1UL.toAttoHeight())
                                    }

                            return@flatMapLatest nodeClient.transactionStream((latestHeights + unknownHeights).toHeightSearch())
                        }.onStart { logger.info { "Started listening transactions" } }
                        .onCompletion { logger.info { "Stopped listening transactions" } }
                        .collect { transaction ->
                            logger.debug { "Saving $transaction" }
                            repository.save(transaction.toTransaction())
                            logger.info { "Saved $transaction" }
                            eventPublisher.publishEvent(TransactionSaved(transaction))
                        }
                } catch (e: Exception) {
                    logger.error(e) { "Error while listening transactions stream. Retrying in 10 seconds..." }
                    delay(10.seconds)
                }
            }
        }
    }

    fun List<PublicKeyHeightView>.toHeightSearch(): HeightSearch {
        val search =
            this.map {
                AccountHeightSearch(AttoAddress(it.algorithm, it.publicKey), it.height + 1U)
            }
        return HeightSearch(search)
    }

    @PreDestroy
    fun close() {
        scope.cancel()
    }
}
