package cash.atto.monitor.utility

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoTransaction
import cash.atto.commons.fromHexToByteArray
import cash.atto.commons.toBuffer
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.io.Buffer
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping
@Tag(
    name = "Utilities",
    description =
        "Provide a range of utilities",
)
class UtilityController {
    @PostMapping("/utilities/block-hex-to-json")
    @Operation(
        summary = "Convert block hex to json block",
        responses = [
            ApiResponse(responseCode = "200"),
            ApiResponse(responseCode = "400"),
        ],
    )
    suspend fun convertBlockToJson(
        @RequestBody hex: String,
    ): AttoBlock {
        return parseHex(hex, "Invalid block hex") { buffer ->
            AttoBlock.fromBuffer(buffer)
        }
    }


    @PostMapping("/utilities/transaction-hex-to-json")
    @Operation(
        summary = "Convert transaction hex to json block",
        responses = [
            ApiResponse(responseCode = "200"),
            ApiResponse(responseCode = "400"),
        ],
    )
    suspend fun convertTransactionToJson(
        @RequestBody hex: String,
    ): AttoTransaction {
        return parseHex(hex, "Invalid transaction hex") { buffer ->
            AttoTransaction.fromBuffer(buffer)
        }
    }

    private fun <T> parseHex(
        raw: String,
        errorMessage: String,
        parser: (buffer: Buffer) -> T?
    ): T {
        val cleanHex = raw.stripQuotes()
        cleanHex.checkHex()

        return parser(cleanHex.fromHexToByteArray().toBuffer())
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage)
    }

    private fun String.stripQuotes(): String =
        this.trim().removePrefix("\"").removeSuffix("\"")

    private fun String.checkHex() {
        if (length % 2 != 0 || !matches(Regex("^[0-9a-fA-F]+$"))) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid hex string")
        }
    }
}
