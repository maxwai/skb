package edu.hm.skb.worker;

import edu.hm.skb.config.Config;
import edu.hm.skb.config.ConfigInjector;
import edu.hm.skb.data.Data;
import edu.hm.skb.util.hash.HashMethod;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 * Worker doing the healthcheck
 */
@ApplicationScoped
public class HealthcheckWorker {

    /**
     * Log instance
     */
    private static final Logger LOG = Logger.getLogger(HealthcheckWorker.class);

    /**
     * Data Interface Instance
     */
    private final Data data = Data.getData();
    /**
     * Queue of blocks that need to be checked
     */
    private final List<Config.Block> blocksToCheck = new ArrayList<>();

    /**
     * Config Instance
     */
    @Inject
    /* default */ ConfigInjector config;

    /**
     * Counter used for the healthcheck interval
     */
    private int counter = -1;

    /**
     * Healthcheck worker
     */
    @Scheduled(every = "1m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void healthcheck() {
        counter++;
        counter %= config.getConfig().getHealthCheckInterval();
        if (counter != 0) {
            return;
        }
        int amountToCheck = (int) (config.getConfig().getBlocks().size() * config.getConfig()
                .getHealthCheckPercent() / 100.0);
        while (amountToCheck > 0) {
            if (blocksToCheck.isEmpty()) {
                blocksToCheck.addAll(config.getConfig().getBlocks());
                Collections.shuffle(blocksToCheck);
            }
            int tmpAmount = Math.min(amountToCheck, blocksToCheck.size());
            blocksToCheck.removeAll(blocksToCheck.stream()
                    .limit(tmpAmount)
                    .filter(block -> block.serverToId().entrySet().stream().map(entry -> {
                        try {
                            if (HashMethod.checkIntegrity(entry.getKey(), config.getConfig()
                                    .getHostname(), entry.getValue(), data, block)) {
                                return true;
                            } else {
                                LOG.warnf(
                                        "Hash wasn't as expected for block {0} (external ID: {1}) on server {2}",
                                        block.id(), entry.getValue(), entry.getKey());
                                // FIXME: handle if hash wasn't correct
                                return false;
                            }
                        } catch (FileNotFoundException e) {
                            LOG.error("Couldn't find local Block", e);
                            return false;
                        } catch (IOException e) {
                            LOG.error("Couldn't read file", e);
                            return false;
                        }
                    }).reduce(true, Boolean::logicalAnd))
                    .toList());
            amountToCheck -= tmpAmount;
        }
    }
}
