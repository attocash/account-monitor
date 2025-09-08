package cash.atto.monitor.convertion

import cash.atto.monitor.toBigInteger
import cash.atto.monitor.toULong
import org.springframework.stereotype.Component
import java.math.BigInteger

@Component
class ULongSerializerDBConverter : DBConverter<ULong, BigInteger> {
    override fun convert(source: ULong): BigInteger = source.toBigInteger()
}

@Component
class ULongDeserializerDBConverter : DBConverter<BigInteger, ULong> {
    override fun convert(source: BigInteger): ULong = source.toULong()
}
