package dio.budgeting.infrastructure.http;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "API de Orçamento",
                description = "API para gerenciamento de transações financeiras, confirmações e assistente de orçamento.",
                version = "1.0.0",
                contact = @Contact(name = "Budgeting API")
        ),
        tags = {
                @Tag(name = "Transações", description = "Operações para cadastro e consulta de transações."),
                @Tag(name = "Assistente IA", description = "Processamento de áudio, recibos, chat e comparação de provedores.")
        }
)
public class OpenApiConfiguration {
}
