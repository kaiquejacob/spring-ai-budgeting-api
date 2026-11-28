package dio.budgeting.application.proposal;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

@Service
public class ExpiredProposalCleanup {

    private final PendingProposalStore proposals;
    private final Clock clock;

    public ExpiredProposalCleanup(PendingProposalStore proposals, Clock clock) {
        this.proposals = proposals;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${budgeting.proposal.cleanup.interval:PT5M}",
            initialDelayString = "${budgeting.proposal.cleanup.interval:PT5M}")
    @Transactional
    public int deleteExpired() {
        // Match DATETIME(6) without rounding the cutoff forward.
        var cutoff = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return proposals.deleteExpiredAtOrBefore(cutoff);
    }
}
