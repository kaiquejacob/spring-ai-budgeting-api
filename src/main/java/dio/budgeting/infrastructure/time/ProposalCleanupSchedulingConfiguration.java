package dio.budgeting.infrastructure.time;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "budgeting.proposal.cleanup.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
class ProposalCleanupSchedulingConfiguration {
}
