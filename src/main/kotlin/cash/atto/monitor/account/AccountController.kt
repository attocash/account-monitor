package cash.atto.monitor.account

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoNetwork
import cash.atto.commons.node.AttoNodeOperations
import cash.atto.commons.serialiazer.AttoAddressAsStringSerializer
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping
@Tag(
    name = "Accounts",
    description =
        "Manage monitored accounts. This controller allows clients to start monitoring a new accounts, enable or disable them. ",
)
class AccountController(
    private val accountService: AccountService,
    private val accountRepository: AccountRepository,
) {
    @PostMapping("/accounts/{address}")
    @Operation(
        summary = "Monitor an account",
        responses = [
            ApiResponse(
                responseCode = "200",
            ),
        ],
    )
    suspend fun monitor(
        @PathVariable address: String,
    ) {
        accountService.create(AttoAddress.parsePath(address))
    }

    @PostMapping("/accounts")
    @Operation(
        summary = "Monitor a list of accounts",
        responses = [
            ApiResponse(
                responseCode = "200",
            ),
        ],
    )
    suspend fun monitor(
        @RequestBody request: MonitorRequest,
    ) {
        request.address.forEach {
            accountService.create(it)
        }
    }

    @GetMapping("/accounts")
    @Operation(
        summary = "Get all accounts",
        responses = [
            ApiResponse(
                responseCode = "200",
                content = [
                    Content(
                        schema = Schema(implementation = Account::class),
                    ),
                ],
            ),
        ],
    )
    fun findAll(): Flow<Account> = accountRepository.findAll()

    @GetMapping("/accounts/{address}")
    @Operation(
        summary = "Get account",
        responses = [
            ApiResponse(
                responseCode = "200",
            ),
            ApiResponse(
                responseCode = "404",
            ),
        ],
    )
    suspend fun get(
        @PathVariable address: String,
    ): ResponseEntity<Account> = ResponseEntity.ofNullable(accountRepository.findById(address))

    @GetMapping("/accounts/{address}/details")
    @Operation(
        summary = "Get account details",
        responses = [
            ApiResponse(
                responseCode = "200",
            ),
            ApiResponse(
                responseCode = "404",
            ),
        ],
    )
    suspend fun getDetails(
        @PathVariable address: String,
    ): ResponseEntity<AttoAccount> = ResponseEntity.ofNullable(accountService.getAccountDetails(AttoAddress.parsePath(address)))

    @PostMapping("/accounts/{address}/states/DISABLED")
    @Operation(
        summary = "Disable an account",
        responses = [
            ApiResponse(
                responseCode = "200",
            ),
        ],
    )
    suspend fun disable(
        @PathVariable address: String,
    ) {
        accountService.disable(AttoAddress.parsePath(address))
    }

    @PostMapping("/accounts/{address}/states/ENABLED")
    @Operation(
        summary = "Enable an account",
        responses = [
            ApiResponse(
                responseCode = "200",
            ),
        ],
    )
    suspend fun enable(
        @PathVariable address: String,
    ) {
        accountService.enable(AttoAddress.parsePath(address))
    }

    @Serializable
    data class MonitorRequest(
        @field:Schema(
            description = "The address of the account being monitored",
        )
        val address: List<
            @Serializable(with = AttoAddressAsStringSerializer::class)
            AttoAddress,
        >,
    )

    @Serializable
    data class AccountHeightSearch(
        @field:Schema(
            description = "The address of the account being searched",
            example = "aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2",
            type = "String",
        )
        @Serializable(with = AttoAddressAsStringSerializer::class)
        val address: AttoAddress,
        val fromHeight: ULong,
    )

    @Serializable
    data class HeightSearch(
        val search: List<AccountHeightSearch>,
    )

    @Schema(name = "AttoBlock", description = "Base type for all block variants")
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type", // this must match the "type" field in your JSON
    )
    @JsonSubTypes(
        JsonSubTypes.Type(value = AttoSendBlockSample::class, name = "SEND"),
        JsonSubTypes.Type(value = AttoReceiveBlockSample::class, name = "RECEIVE"),
        JsonSubTypes.Type(value = AttoOpenBlockSample::class, name = "OPEN"),
        JsonSubTypes.Type(value = AttoChangeBlockSample::class, name = "CHANGE"),
    )
    sealed interface AttoBlockSample {
        val network: AttoNetwork
        val version: Int
        val algorithm: AttoAlgorithm
        val publicKey: String
        val balance: BigDecimal
        val timestamp: Long
    }

    @Schema(name = "AttoSendBlock", description = "Represents a SEND block")
    data class AttoSendBlockSample(
        override val network: AttoNetwork,
        override val version: Int,
        override val algorithm: AttoAlgorithm,
        override val publicKey: String,
        override val balance: BigDecimal,
        override val timestamp: Long,
        @param:Schema(description = "Height of the block", example = "2")
        val height: BigDecimal,
        @param:Schema(
            description = "Hash of the previous block",
            example = "6CC2D3A7513723B1BA59DE784BA546BAF6447464D0BA3D80004752D6F9F4BA23",
        )
        val previous: String,
        @param:Schema(description = "Algorithm of the receiver", example = "V1")
        val receiverAlgorithm: AttoAlgorithm,
        @param:Schema(
            description = "Public key of the receiver",
            example = "552254E101B51B22080D084C12C94BF7DFC5BE0D973025D62C0BC1FF4D9B145F",
        )
        val receiverPublicKey: String,
        @param:Schema(description = "Amount being sent", example = "1")
        val amount: BigDecimal,
    ) : AttoBlockSample

    @Schema(name = "AttoReceiveBlock", description = "Represents a RECEIVE block")
    data class AttoReceiveBlockSample(
        override val network: AttoNetwork,
        override val version: Int,
        override val algorithm: AttoAlgorithm,
        override val publicKey: String,
        override val balance: BigDecimal,
        override val timestamp: Long,
        @param:Schema(description = "Height of the block", example = "2")
        val height: BigDecimal,
        @param:Schema(
            description = "Hash of the previous block",
            example = "03783A08F51486A66A602439D9164894F07F150B548911086DAE4E4F57A9C4DD",
        )
        val previous: String,
        @param:Schema(description = "Algorithm of the send block", example = "V1")
        val sendHashAlgorithm: AttoAlgorithm,
        @param:Schema(description = "Hash of the send block", example = "EE5FDA9A1ACEC7A09231792C345CDF5CD29F1059E5C413535D9FCA66A1FB2F49")
        val sendHash: String,
    ) : AttoBlockSample

    @Schema(name = "AttoOpenBlock", description = "Represents an OPEN block")
    data class AttoOpenBlockSample(
        override val network: AttoNetwork,
        override val version: Int,
        override val algorithm: AttoAlgorithm,
        override val publicKey: String,
        override val balance: BigDecimal,
        override val timestamp: Long,
        @param:Schema(description = "Algorithm of the send block", example = "V1")
        val sendHashAlgorithm: AttoAlgorithm,
        @param:Schema(description = "Hash of the send block", example = "4DC7257C0F492B8C7AC2D8DE4A6DC4078B060BB42FDB6F8032A839AAA9048DB0")
        val sendHash: String,
        @param:Schema(description = "Algorithm of the representative", example = "V1")
        val representativeAlgorithm: AttoAlgorithm,
        @Schema(
            description = "Public key of the representative",
            example = "69C010A8A74924D083D1FC8234861B4B357530F42341484B4EBDA6B99F047105",
        )
        val representativePublicKey: String,
    ) : AttoBlockSample

    @Schema(name = "AttoChangeBlock", description = "Represents a CHANGE block")
    data class AttoChangeBlockSample(
        override val network: AttoNetwork,
        override val version: Int,
        override val algorithm: AttoAlgorithm,
        override val publicKey: String,
        override val balance: BigDecimal,
        override val timestamp: Long,
        @param:Schema(description = "Height of the block", example = "2")
        val height: BigDecimal,
        @param:Schema(
            description = "Hash of the previous block",
            example = "AD675BD718F3D96F9B89C58A8BF80741D5EDB6741D235B070D56E84098894DD5",
        )
        val previous: String,
        @param:Schema(description = "Algorithm of the representative", example = "V1")
        val representativeAlgorithm: AttoAlgorithm,
        @param:Schema(
            description = "Public key of the representative",
            example = "69C010A8A74924D083D1FC8234861B4B357530F42341484B4EBDA6B99F047105",
        )
        val representativePublicKey: String,
    ) : AttoBlockSample

    @Schema(name = "AttoTransaction", description = "A signed block")
    data class AttoTransactionSample(
        @param:Schema(description = "The block to be submitted (SEND, RECEIVE, OPEN, CHANGE)")
        val block: AttoBlockSample,
        @param:Schema(
            description = "Ed25519 signature of the block",
            example =
                "52843B36ABDFA4125E4C0D465A3C976C269F993C7C82645B29AB49B7A5A84FC41E" +
                    "3391D2A41C4CB83DFA3214DA87B099F86EF10402BFB1120A5D34F70CBC2B00",
        )
        val signature: String,
        @param:Schema(
            description = "Proof-of-work for the block",
            example = "4300FFFFFFFFFFCF",
        )
        val work: String,
    )
}
