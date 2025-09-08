package cash.atto.monitor.account

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAddress
import cash.atto.commons.node.AttoNodeOperations
import cash.atto.monitor.CacheSupport
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.context.annotation.DependsOn
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

@Service
@DependsOn("flywayInitializer")
class AccountService(
    private val accountRepository: AccountRepository,
    private val nodeClient: AttoNodeOperations,
) : CacheSupport {
    private val logger = KotlinLogging.logger {}

    private val scope = CoroutineScope(Dispatchers.Default)

    private val activeAddresses = MutableStateFlow<Set<AttoAddress>>(emptySet())

    private val accountStateMap = ConcurrentHashMap<AttoAddress, AccountState>()

    override fun clear() {
        activeAddresses.value = emptySet()
    }

    fun getAccountMap(): Map<AttoAddress, AttoAccount?> = accountStateMap.mapValues { it.value.attoAccount }.toMap()

    fun getAccountDetails(address: AttoAddress): AttoAccount? = accountStateMap[address]?.attoAccount

    fun streamActiveAddressUpdates(): Flow<Set<AttoAddress>> = activeAddresses

    @PostConstruct
    fun init() {
        runBlocking {
            val accounts = accountRepository.findAll().toList()

            val attoAccountMap: Map<AttoAddress, AttoAccount> =
                nodeClient
                    .account(accounts.map { AttoAddress.parsePath(it.address) })
                    .associateBy { it.address }

            logger.info("Found ${accounts.size} addresses")

            accountRepository.findAll().collect { account ->
                val address = AttoAddress.parsePath(account.address)
                val accountState = AccountState(AttoAddress.parsePath(account.address), account, attoAccountMap[address])
                accountStateMap[address] = accountState
            }

            logger.info("Loaded ${accounts.size} addresses")

            startStream()
        }
    }

    private suspend fun refreshActiveAddresses() {
        val newActiveAddresses =
            accountStateMap.values
                .filter { it.isEnabled() }
                .map { it.address }
                .toSet()
        activeAddresses.emit(newActiveAddresses)
        logger.info { "Refreshed ${newActiveAddresses.size} active addresses" }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startStream() {
        scope.launch {
            while (isActive) {
                try {
                    activeAddresses
                        .onStart { refreshActiveAddresses() }
                        .flatMapLatest { addresses ->
                            if (addresses.isEmpty()) {
                                return@flatMapLatest emptyFlow()
                            }
                            return@flatMapLatest nodeClient.accountStream(addresses)
                        }.onStart { logger.info { "Started listening account" } }
                        .onCompletion { logger.info { "Stopped listening account" } }
                        .collect { account ->
                            logger.debug { "Updating $account" }
                            accountStateMap.computeIfPresent(account.address) { _, state ->
                                state.copy(attoAccount = account)
                            }
                            logger.info { "Updated $account" }
                        }
                } catch (e: Exception) {
                    logger.error(e) { "Error while listening account stream. Retrying in 10 seconds..." }
                    delay(10.seconds)
                }
            }
        }
    }

    @PreDestroy
    fun close() {
        scope.cancel()
    }

    @Transactional
    suspend fun create(address: AttoAddress): Account {
        logger.info { "Start monitoring of account $address" }
        accountRepository.findById(address.path)?.let {
            logger.info { "Account $address is already known. Enabling instead..." }

            return enable(address)
        }

        val account = accountRepository.save(Account(address.path))

        accountStateMap.computeIfAbsent(address) { _ ->
            AccountState(address, account)
        }

        refreshActiveAddresses()

        logger.info { "Account $address monitoring added successfully." }

        return account
    }

    suspend fun AccountRepository.getAccount(address: AttoAddress): Account =
        findById(address.path) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Address $address not found")

    @Transactional
    suspend fun disable(address: AttoAddress): Account {
        val account = accountRepository.getAccount(address)

        if (account.disabledAt != null) {
            logger.info { "Account $address is already disabled" }
            return account
        }

        account.disabledAt = Instant.now()

        accountRepository.save(account)

        accountStateMap.computeIfPresent(address) { _, state ->
            state.copy(account = account)
        }

        refreshActiveAddresses()

        logger.info { "Account $address has been disabled at ${account.disabledAt}" }

        return account
    }

    @Transactional
    suspend fun enable(address: AttoAddress): Account {
        val account = accountRepository.getAccount(address)

        if (account.disabledAt == null) {
            logger.info { "Account $address is already enabled" }
            return account
        }

        account.disabledAt = null

        accountRepository.save(account)

        accountStateMap.computeIfPresent(address) { _, state ->
            state.copy(account = account)
        }

        refreshActiveAddresses()

        logger.info { "Account $address has been enabled" }

        return account
    }

    private data class AccountState(
        val address: AttoAddress,
        val account: Account,
        val attoAccount: AttoAccount? = null,
    ) {
        fun isEnabled(): Boolean = account.disabledAt == null
    }
}
