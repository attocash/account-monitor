package cash.atto.monitor.transaction

import cash.atto.commons.AttoTransaction
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping
@Tag(
    name = "Transactions",
    description = "Query raw transaction blocks. This endpoint handles the low-level building blocks of the ledger.",
)
class TransactionController(
    private val repository: TransactionRepository,
) {
    @GetMapping("/transactions")
    @Operation(
        summary = "Get all account entries between the specified dates",
        responses = [
            ApiResponse(
                responseCode = "200",
            ),
        ],
    )
    fun getAll(
        @RequestParam persistedAtFrom: Instant,
        @RequestParam persistedAtTo: Instant,
    ): Flow<AttoTransaction> = repository.findAllByPersistedAtBetween(persistedAtFrom, persistedAtTo).map { it.toAttoTransaction() }
}
