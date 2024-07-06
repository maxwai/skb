package edu.hm.skb.worker;

import edu.hm.skb.api.backup.BackupService;
import edu.hm.skb.api.fed.FedService;
import edu.hm.skb.config.Config;
import edu.hm.skb.config.ConfigInjector;
import edu.hm.skb.data.Data;
import edu.hm.skb.util.hash.HashMethod;
import edu.hm.skb.util.model.FedInfoResponse;
import edu.hm.skb.util.model.Field;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Worker doing the Backups
 */
@ApplicationScoped
public class BackupWorker {

    /**
     * Queue of blocks that needs to be either updated, deleted or created on remote servers
     */
    public static final BlockingQueue<Config.Block> BLOCKS_TO_CHECK = new LinkedBlockingQueue<>();
    /**
     * Log instance
     */
    private static final Logger LOG = Logger.getLogger(BackupWorker.class);

    /**
     * Data Interface Instance
     */
    private final Data data = Data.getData();

    /**
     * Config Instance
     */
    @Inject
    /* default */ ConfigInjector config;

    /**
     * Calculate gcd
     *
     * @param a number 1
     * @param b number 2
     * @return the gcd
     */
    @SuppressWarnings("PMD.AvoidReassigningParameters")
    private static long gcd(long a, long b) {
        while (b > 0) {
            long temp = b;
            b = a % b; // % is remainder
            a = temp;
        }
        return a;
    }

    /**
     * Calculate lcm
     *
     * @param a number 1
     * @param b number 2
     * @return the lcm
     */
    private static long lcm(long a, long b) {
        return a * (b / gcd(a, b));
    }

    /**
     * Runs every 10m and will check every block if something needs to be done
     */
    @Scheduled(every = "10m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void backupBlocks() {
        List<Config.Block> blocks = config.getConfig().getBlocks();
        List<Config.Server> servers = config.getConfig()
                .getServers()
                .stream()
                .filter(Config.Server::isVerified)
                .filter(server -> {
                    return FedService.getFedRestClient(server.hostname())
                            .getServerInfo(config.getConfig().getHostname()).is_verified;
                })
                .toList();
        blocks.stream().filter(block -> block.serverToId().size() < 2).forEach(block -> {
            List<Config.Server> newServer = servers.stream()
                    .filter(server -> !block.serverToId().containsKey(server.hostname()))
                    .toList();
            if (newServer.isEmpty()) {
                return;
            }
            for (Config.Server server : newServer) {
                List<Field.BlockInfo> remoteBlocks = FedService.getFedRestClient(server.hostname())
                        .getBlocks(config.getConfig().getHostname())
                        .blocks();
                List<Field.BlockInfo> freeBlocks = remoteBlocks.stream()
                        .filter(blockInfo -> blockInfo.last_modified() == 0)
                        .toList();
                if (!freeBlocks.isEmpty()) {
                    uploadNewBlock(server, freeBlocks.get(0), block);
                    return;
                }
            }

            // there was no server that had empty reserved blocks for us
            for (Config.Server server : newServer) {
                FedInfoResponse serverInfo = FedService.getFedRestClient(server.hostname())
                        .getServerInfo(config.getConfig().getHostname());
                long sizeReserved = lcm(serverInfo.block_size, config.getConfig().getBlockSize());
                int amountOwnBlocks;
                int amountRemoteBlocks;
                try {
                    amountOwnBlocks = Math.toIntExact(sizeReserved / config.getConfig()
                            .getBlockSize());
                    amountRemoteBlocks = Math.toIntExact(sizeReserved / serverInfo.block_size);
                } catch (ArithmeticException e) {
                    LOG.fatal("Got an int overflow where never possible", e);
                    return;
                }
                if (amountOwnBlocks > data
                        .getFreeExternalBlocks() || amountRemoteBlocks > serverInfo.free_blocks) {
                    // there aren't that many blocks free
                    continue;
                }
                List<Config.ExternalBlock> newBlocks = new ArrayList<>();
                for (int i = 0; i < amountOwnBlocks; i++) {
                    int counter = 0;
                    Config.ExternalBlock newBlock;
                    do {
                        newBlock = new Config.ExternalBlock(UUID.randomUUID().toString(), server
                                .hostname());
                        counter++;
                    } while (counter < 1000 && !config.getConfig().addNewExternalBlock(newBlock));
                    if (counter == 1000) {
                        LOG.error("Couldn't create new external Block");
                        newBlocks.forEach(externalBlock -> config.getConfig()
                                .deleteExternalBlock(externalBlock.id()));
                        return;
                    }
                    newBlocks.add(newBlock);
                }
                try (Response response = FedService.getFedRestClient(server.hostname())
                        .reserveBlocks(config.getConfig().getHostname(), new Field.Amount(
                                amountRemoteBlocks))) {
                    if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                        LOG.warnf("Remote server {0} wouldn't create new blocks", server
                                .hostname());
                        newBlocks.forEach(externalBlock -> config.getConfig()
                                .deleteExternalBlock(externalBlock.id()));
                        continue;
                    }
                }
                List<Field.BlockInfo> remoteBlocks = FedService.getFedRestClient(server.hostname())
                        .getBlocks(config.getConfig().getHostname())
                        .blocks();
                List<Field.BlockInfo> freeBlocks = remoteBlocks.stream()
                        .filter(blockInfo -> blockInfo.last_modified() == 0)
                        .toList();
                if (!freeBlocks.isEmpty()) {
                    uploadNewBlock(server, freeBlocks.get(0), block);
                    break;
                }
                LOG.errorf("Server {0} didn't reserve the blocks as wanted", server.hostname());
                newBlocks.forEach(externalBlock -> config.getConfig()
                        .deleteExternalBlock(externalBlock.id()));
            }
            // There is no server available with enough place where we could put the block
        });
    }

    /**
     * Runs every 10s and only checks the blocks in the queue
     */
    @Scheduled(every = "10s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void backupKnownBlocks() {
        List<Config.Block> blocksToCheck = new ArrayList<>();
        BLOCKS_TO_CHECK.drainTo(blocksToCheck);
        blocksToCheck.forEach(block -> {
            Config.Block foundBlock = config.getConfig().getBlock(block.id());
            if (foundBlock == null) {
                // Block was deleted
                block.serverToId().forEach((hostname, remoteId) -> {
                    try (Response response = FedService.getFedRestClient(hostname)
                            .deleteBlock(config.getConfig().getHostname(), remoteId)) {
                        if (response.getStatusInfo()
                                .getFamily() != Response.Status.Family.SUCCESSFUL && response
                                        .getStatus() != Response.Status.NOT_FOUND.getStatusCode()) {
                            LOG.errorf(
                                    "Deleted Block {0} on server {1} with id {2} couldn't be deleted on the remote server",
                                    block.id(), hostname, remoteId);
                            // FIXME: what to do in this case?
                        }
                    }
                });
            } else {
                // Block was updated or created
                // we only update the blocks on already saved servers, new servers are added in the
                // other schedule
                foundBlock.serverToId().forEach((hostname, remoteId) -> {
                    Field.Jwt jwt = FedService.getFedRestClient(hostname)
                            .getBlockJwt(config.getConfig().getHostname(), remoteId);
                    byte[] blockData;
                    try (InputStream blockStream = data.getBlock(foundBlock)) {
                        blockData = blockStream.readAllBytes();
                    } catch (IOException e) {
                        LOG.error("Problem reading block content", e);
                        BLOCKS_TO_CHECK.add(foundBlock);
                        return;
                    }
                    try (Response response = BackupService.getBakRestClient(hostname)
                            .blockUpdate(jwt.jwt(), remoteId, blockData)) {
                        if (response.getStatusInfo()
                                .getFamily() == Response.Status.Family.SUCCESSFUL) {
                            try {
                                if (!HashMethod.checkIntegrity(hostname, config.getConfig()
                                        .getHostname(), remoteId, data, foundBlock)) {
                                    LOG.warnf(
                                            "Hash wasn't as expected for block {0} (external ID: {1}) on server {2}",
                                            foundBlock.id(), remoteId, hostname);
                                    // FIXME: handle if hash wasn't correct
                                }
                            } catch (FileNotFoundException e) {
                                LOG.error("Couldn't find local Block", e);
                                // TODO: handle error
                            } catch (IOException e) {
                                LOG.error("Couldn't read file", e);
                                // TODO: handle error
                            }
                        } else {
                            LOG.warnf("Update of block {0} to server {1} not successful", foundBlock
                                    .id(), hostname);
                            BLOCKS_TO_CHECK.add(foundBlock);
                        }
                    }
                });
            }
        });
    }

    /**
     * Uploads a new block to a server
     *
     * @param server    the remote server
     * @param freeBlock the block to upload to
     * @param block     the block to upload
     */
    private void uploadNewBlock(Config.Server server, Field.BlockInfo freeBlock,
            Config.Block block) {
        Field.Jwt jwt = FedService.getFedRestClient(server.hostname())
                .getBlockJwt(config.getConfig().getHostname(), freeBlock.id());
        byte[] blockData;
        try (InputStream blockStream = data.getBlock(block)) {
            blockData = blockStream.readAllBytes();
        } catch (IOException e) {
            LOG.error("Problem reading block content", e);
            return;
        }
        try (Response response = BackupService.getBakRestClient(server.hostname())
                .blockUpload(jwt.jwt(), freeBlock.id(), blockData)) {
            if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
                try {
                    if (HashMethod.checkIntegrity(server.hostname(), config.getConfig()
                            .getHostname(), freeBlock.id(), data, block)) {
                        block.serverToId().put(server.hostname(), freeBlock.id());
                        if (!config.getConfig()
                                .addBlockServer(block.id(), server.hostname(), freeBlock.id())) {
                            throw new IllegalStateException();
                        }
                    } else {
                        LOG.warnf(
                                "Hash wasn't as expected for block {0} (external ID: {1}) on server {2}",
                                block.id(), freeBlock.id(), server.hostname());
                        // FIXME: handle if hash wasn't correct
                    }
                } catch (FileNotFoundException e) {
                    LOG.error("Couldn't find local Block", e);
                    // TODO: handle error
                } catch (IOException e) {
                    LOG.error("Couldn't read file", e);
                    // TODO: handle error
                }

            } else {
                LOG.warnf("Upload of block {0} to server {1} not successful", block.id(), server
                        .hostname());
            }
        }
    }
}
