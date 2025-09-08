package cash.atto.monitor.account.entry

import cash.atto.commons.AttoAccountEntry
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
    name = "Account Entries",
    description = "A user-friendly view of account activity. Recommended for displaying transaction history in UIs.",
)
class AccountEntryController(
    private val repository: AccountEntryRepository,
) {
    @GetMapping("/accounts/entries")
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
    ): Flow<AttoAccountEntry> = repository.findAllByPersistedAtBetween(persistedAtFrom, persistedAtTo).map { it.toAtto() }
}
