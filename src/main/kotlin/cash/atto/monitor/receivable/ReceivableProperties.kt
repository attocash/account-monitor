package cash.atto.monitor.receivable

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "atto.receivable")
class ReceivableProperties {
    lateinit var minAmount: String
}
