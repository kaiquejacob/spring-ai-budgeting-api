package dio.budgeting.infrastructure.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        AiProperties.class,
        GeminiProperties.class
})
public class AiConfiguration {
}