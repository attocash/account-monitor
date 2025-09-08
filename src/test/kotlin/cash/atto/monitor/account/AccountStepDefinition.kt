package cash.atto.monitor.account

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoBlockType
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoReceiveBlock
import cash.atto.commons.AttoTransaction
import cash.atto.commons.AttoUnit
import cash.atto.commons.node.AttoMockNode
import cash.atto.commons.sign
import cash.atto.commons.toAddress
import cash.atto.commons.toAttoHeight
import cash.atto.commons.toAttoVersion
import cash.atto.commons.toPublicKey
import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.cpu
import cash.atto.monitor.CacheSupport
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.springframework.boot.test.web.client.TestRestTemplate
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days

class AccountStepDefinition(
    private val testRestTemplate: TestRestTemplate,
    private val mockNode: AttoMockNode,
) : CacheSupport {
    private var holder: KeyHolder? = null
    var entry: AttoAccountEntry? = null
    var transaction: AttoTransaction? = null

    @When("an account is monitored")
    fun create() {
        holder = KeyHolder()
        testRestTemplate.postForLocation("/accounts/${holder!!.address.path}", null)
    }

    @When("address is disabled")
    fun disable() {
        testRestTemplate.postForLocation("/accounts/${holder!!.address.path}/states/DISABLED", null)
    }

    @When("address is enabled")
    fun enable() {
        testRestTemplate.postForLocation("/accounts/${holder!!.address.path}/states/ENABLED", null)
    }

    @When("a receivable is published")
    fun publishReceivable() {
        val receivable =
            AttoReceivable(
                version = 0U.toAttoVersion(),
                algorithm = AttoAlgorithm.V1,
                publicKey = holder!!.publicKey,
                receiverAlgorithm = AttoAlgorithm.V1,
                receiverPublicKey = holder!!.publicKey,
                amount = AttoAmount.from(AttoUnit.ATTO, "100"),
                timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds() - 10000L),
                hash = AttoHash(Random.Default.nextBytes(32)),
            )
        runBlocking {
            mockNode.receivableFlow.emit(receivable)
        }
    }

    @When("a transaction is published")
    fun publishTransaction() {
        val receiveBlock =
            AttoReceiveBlock(
                version = 0U.toAttoVersion(),
                network = AttoNetwork.LOCAL,
                algorithm = holder!!.address.algorithm,
                publicKey = holder!!.publicKey,
                height = 2U.toAttoHeight(),
                balance = AttoAmount.MAX,
                timestamp = Clock.System.now(),
                previous = AttoHash(Random.nextBytes(ByteArray(32))),
                sendHashAlgorithm = AttoAlgorithm.V1,
                sendHash = AttoHash(Random.Default.nextBytes(ByteArray(32))),
            )

        runBlocking {
            transaction =
                AttoTransaction(
                    block = receiveBlock,
                    signature = holder!!.privateKey.sign(receiveBlock.hash),
                    work = AttoWorker.cpu().work(receiveBlock),
                )

            mockNode.transactionFlow.emit(transaction!!)
        }
    }

    @When("an account entry is published")
    fun publishAccountEntry() {
        this.entry =
            AttoAccountEntry(
                hash = AttoHash(Random.nextBytes(32)),
                algorithm = holder!!.address.algorithm,
                publicKey = holder!!.publicKey,
                height = AttoHeight(2U),
                blockType = AttoBlockType.RECEIVE,
                subjectAlgorithm = AttoAlgorithm.V1,
                subjectPublicKey = AttoPublicKey(Random.nextBytes(32)),
                previousBalance = AttoAmount(0U),
                balance = AttoAmount(100U),
                timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
            )

        runBlocking {
            mockNode.accountEntryFlow.emit(entry!!)
        }
    }

    @Then("transaction is persisted")
    fun checkTransaction() {
        val from = Clock.System.now().minus(1.days)
        val to = Clock.System.now().plus(1.days)
        val transactions =
            testRestTemplate.getForObject("/transactions?persistedAtFrom=$from&persistedAtTo=$to", Array<AttoTransaction>::class.java)
        assertEquals(listOf(transaction!!.hash), transactions.map { it.hash }.toList())
    }

    @Then("account entry is persisted")
    fun checkAccountEntry() {
        val from = Clock.System.now().minus(1.days)
        val to = Clock.System.now().plus(1.days)
        val entries =
            testRestTemplate.getForObject("/accounts/entries?persistedAtFrom=$from&persistedAtTo=$to", Array<AttoAccountEntry>::class.java)
        assertEquals(listOf(entry), entries.toList())
    }

    private fun getAccount(): Account = testRestTemplate.getForObject("/accounts/${holder!!.address.path}", Account::class.java)

    private fun getAccountDetails(): AttoAccount? =
        testRestTemplate.getForObject("/accounts/${holder!!.address.path}/details", AttoAccount::class.java)

    @Then("account should be created")
    fun checkCreated() {
        val response = getAccount()
        assertEquals(holder!!.address.path, response.address)
    }

    @Then("account should be disabled")
    fun checkDisabled() {
        val response = getAccount()
        assertNotNull(response.disabledAt)
    }

    @Then("account should be enabled")
    fun checkEnabled() {
        val response = getAccount()
        assertNull(response.disabledAt)
    }

    override fun clear() {
        entry = null
        transaction = null
    }

    private data class KeyHolder(
        val privateKey: AttoPrivateKey = AttoPrivateKey.generate(),
    ) {
        val publicKey = privateKey.toPublicKey()
        var address = publicKey.toAddress(AttoAlgorithm.V1)
    }
}
