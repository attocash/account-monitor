package cash.atto.monitor.account

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.core.annotation.Order
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.domain.Persistable
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.time.Instant

@Order(1)
interface AccountRepository : CoroutineCrudRepository<Account, String>

class Account(
    @Schema(description = "The address of the account", example = "aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2")
    @Id
    val address: String,
    @Schema(
        description = "The database version of the row used for optimistic locking",
        example = "1",
    )
    @Version
    val version: Long? = null,
    @JsonIgnore
    val persistedAt: Instant? = null,
    @JsonIgnore
    val updatedAt: Instant? = null,
    @Schema(
        description = "Timestamp when the account was disabled",
        example = "treasury",
    )
    var disabledAt: Instant? = null,
) : Persistable<String> {
    @JsonIgnore
    override fun getId(): String = address

    @JsonIgnore
    override fun isNew(): Boolean = persistedAt == null
}
