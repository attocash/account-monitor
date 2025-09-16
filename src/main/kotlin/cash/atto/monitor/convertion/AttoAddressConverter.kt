package cash.atto.monitor.convertion

import cash.atto.commons.AttoAddress
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

@Component
@ConfigurationPropertiesBinding
class AttoAddressConverter : Converter<String, AttoAddress> {
    override fun convert(source: String): AttoAddress = AttoAddress.parsePath(source)
}

