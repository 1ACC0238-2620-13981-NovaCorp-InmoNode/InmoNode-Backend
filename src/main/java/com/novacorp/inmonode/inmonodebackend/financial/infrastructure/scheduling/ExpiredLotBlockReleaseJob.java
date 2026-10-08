package com.novacorp.inmonode.inmonodebackend.financial.infrastructure.scheduling;

import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.ReleaseExpiredLotBlocksCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ReservationCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lot Block: when a block runs out without payment evidence, the lot is available again. An expired block already
 * counts as available for new reservations; this job brings the stored status up to date so the catalog and the
 * field portfolio show the lot as available.
 */
@Component
@ConditionalOnProperty(name = "financial.lot-blocks.release-job.enabled", havingValue = "true", matchIfMissing = true)
public class ExpiredLotBlockReleaseJob {

    private static final Logger LOG = LoggerFactory.getLogger(ExpiredLotBlockReleaseJob.class);

    private final ReservationCommandService reservationCommandService;

    public ExpiredLotBlockReleaseJob(ReservationCommandService reservationCommandService) {
        this.reservationCommandService = reservationCommandService;
    }

    @Scheduled(fixedDelayString = "${financial.lot-blocks.release-interval:PT1M}")
    public void releaseExpiredBlocks() {
        var released = reservationCommandService.handle(new ReleaseExpiredLotBlocksCommand());
        if (released > 0) {
            LOG.info("Released {} expired lot block(s)", released);
        }
    }
}
