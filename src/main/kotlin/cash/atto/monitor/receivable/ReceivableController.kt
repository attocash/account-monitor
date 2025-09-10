package cash.atto.monitor.receivable

import cash.atto.commons.AttoReceivable
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping
@Tag(
    name = "Receivables",
    description =
        "Displays pending incoming funds. When someone sends a transaction, " +
            "it becomes a \"receivable\" until the recipient explicitly receives it.",
)
class ReceivableController(
    private val receivableService: ReceivableService,
) {
    @GetMapping("/accounts/receivables")
    @Operation(
        summary = "Get all receivables",
        responses = [
            ApiResponse(
                responseCode = "200",
            ),
        ],
    )
    fun getAll(@RequestParam(required = false) limit: Int = Int.MAX_VALUE): Collection<AttoReceivable> =
        receivableService.getReceivables().take(limit)
}
