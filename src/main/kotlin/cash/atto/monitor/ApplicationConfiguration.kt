package cash.atto.monitor

import io.swagger.v3.oas.models.ExternalDocumentation
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeReference
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.scheduling.annotation.EnableScheduling

@ImportRuntimeHints(SpringDocWorkaround::class)
@Configuration
@EnableScheduling
class ApplicationConfiguration {
    @Bean
    fun walletServerOpenAPI(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Atto Account Monitor API")
                    .description(
                        "The Atto Account Monitor provides a lightweight, self-hostable service to watch " +
                            "Atto accounts in real time. It does not manage keys or funds, but allows " +
                            "applications to track account activity, monitor balances, and detect " +
                            "incoming or outgoing transactions without directly interacting with an Atto node.",
                    ).version("v1.0.0"),
            ).externalDocs(
                ExternalDocumentation()
                    .description("Integration Docs")
                    .url("https://atto.cash/docs/integration"),
            )
}



class SpringDocWorkaround1 : RuntimeHintsRegistrar {
    override fun registerHints(
        hints: RuntimeHints,
        classLoader: ClassLoader?,
    ) {
        hints.reflection().registerType(
            TypeReference.of("org.springframework.core.convert.support.GenericConversionService\$Converters"),
            *MemberCategory.entries.toTypedArray(),
        )
    }
}

class SpringDocWorkaround2 : RuntimeHintsRegistrar {
    override fun registerHints(
        hints: RuntimeHints,
        cl: ClassLoader?,
    ) {
        hints.reflection().registerType(
            AttoTransaction::class.java,
            MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
            MemberCategory.INVOKE_PUBLIC_METHODS,
        )

        hints.reflection().registerType(
            TypeReference.of("cash.atto.commons.AttoTransaction[]"),
            MemberCategory.UNSAFE_ALLOCATED,
        )
    }
}

