package cash.atto.monitor.account.entry

import cash.atto.commons.AttoAddress
import cash.atto.commons.node.AccountHeightSearch
import cash.atto.commons.node.AttoNodeOperations
import cash.atto.commons.node.HeightSearch
import cash.atto.commons.toAttoHeight
import cash.atto.monitor.account.AccountService
import cash.atto.monitor.transaction.PublicKeyHeightView
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
import org.springframework.stereotype.Service
import kotlin.time.Duration.Companion.seconds

@Service
@DependsOn("flywayInitializer")
class AccountEntryService(
    private val accountService: AccountService,
    private val repository: AccountEntryRepository,
    private val nodeClient: AttoNodeOperations,
) {
    private val logger = KotlinLogging.logger {}

    private val scope = CoroutineScope(Dispatchers.Default)

    @PostConstruct
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startReceivableStream() {
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
                                        PublicKeyHeightView(it.algorithm, it.publicKey, 0UL.toAttoHeight())
                                    }

                            return@flatMapLatest nodeClient.accountEntryStream((latestHeights + unknownHeights).toHeightSearch())
                        }.onStart { logger.info { "Started listening account entries" } }
                        .onCompletion { logger.info { "Stopped listening account entries" } }
                        .collect { entry ->
                            logger.debug { "Saving $entry" }
                            repository.save(entry.toEntity())
                            logger.info { "Saved $entry" }
                        }
                } catch (e: Exception) {
                    logger.error(e) { "Error while listening account entries stream. Retrying in 10 seconds..." }
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
