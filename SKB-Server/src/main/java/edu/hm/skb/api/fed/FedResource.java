package edu.hm.skb.api.fed;

import edu.hm.skb.api.security.HostnameCheck;
import edu.hm.skb.config.Config;
import edu.hm.skb.config.ConfigInjector;
import edu.hm.skb.data.Data;
import edu.hm.skb.util.JwtUtil;
import edu.hm.skb.util.WordListBean;
import edu.hm.skb.util.hash.HashMethod;
import edu.hm.skb.util.model.FedInfoResponse;
import edu.hm.skb.util.model.Field;
import edu.hm.skb.worker.BackupWorker;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 * API Paths for the Federated REST API
 * <p/>
 * Every request is authenticated with mTLS
 */
@Path("/api/fed/v1")
@HostnameCheck
public class FedResource {

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
     * Word list to create backup codes
     */
    @Inject
    /* default */ WordListBean wordListBean;

    /**
     * JWT Instance to manage JWTs
     */
    @Inject
    /* default */ JwtUtil jwtUtil;

    /**
     * API Path to get the server information
     *
     * @param host the server making the request
     * @return the server information
     */
    @GET
    @Path("/server/info")
    @Produces(MediaType.APPLICATION_JSON)
    public FedInfoResponse serverGetInfo(@HeaderParam("domain") String host) {
        final Config.Server remoteServer = config.getConfig().getServer(host);
        return new FedInfoResponse(config.getConfig().getHostname(), config.getConfig().getOwner(),
                config.getConfig().getBlockSize(), data.getFreeExternalBlocks(), config.getConfig()
                        .getHealthCheckPercent(), config.getConfig().getHealthCheckInterval(),
                HashMethod.getHashMethods().stream().map(HashMethod::getHashMethodName).toList(),
                remoteServer != null && remoteServer.isVerified(), config.getConfig()
                        .getServers()
                        .stream()
                        .filter(server -> server != remoteServer)
                        .map(Config.Server::hostname)
                        .toList());
    }

    /**
     * API Path to verify a server
     *
     * @param host the server making the request
     * @return Backup Code with code 202 if the server is verified or code 209 if the server isn't
     *         yet verified
     */
    @PUT
    @Path("/server/verify")
    @Produces(MediaType.APPLICATION_JSON)
    public Response serverVerify(@HeaderParam("domain") String host) {
        Config.Server remoteServer = config.getConfig().getServer(host);
        if (remoteServer == null) {
            String backupCode = wordListBean.generateBackupCode(config.getConfig(), 5);
            if (backupCode == null) {
                throw new WebApplicationException("Backup Code couldn't be generated");
            }
            remoteServer = new Config.Server(host, Collections.emptyList(), false, true, null, null,
                    backupCode);
            if (!config.getConfig().addNewServer(remoteServer)) {
                throw new IllegalStateException();
            }
        }
        final String backupCode = remoteServer.backupCode();
        return Response.status(remoteServer.isVerified() ? 202 : 209)
                .entity(new Field.BackupCode(backupCode))
                .build();
    }

    /**
     * API Path to notify that the server was verified
     *
     * @param host the server making the request
     * @return the Backup Code if successful. Can also return 404.
     */
    @POST
    @Path("/server/verify")
    @Produces(MediaType.APPLICATION_JSON)
    public Field.BackupCode serverVerified(@HeaderParam("domain") String host) {
        final Config.Server remoteServer = config.getConfig().getServer(host);
        if (remoteServer == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        // Blocks will be updated by BackupService on a schedule
        return new Field.BackupCode(remoteServer.backupCode());
    }

    /**
     * API Path to restore a server with a new hostname with a given backup code.
     * <p/>
     * Return 201 if successful. Can also return 404.
     *
     * @param host       the server making the request
     * @param backupCode the backup Code
     */
    @PUT
    @Path("/server/restore")
    @Consumes(MediaType.APPLICATION_JSON)
    public void serverRestore(@HeaderParam("domain") String host, Field.BackupCode backupCode) {
        final List<Config.Server> remoteServer = config.getConfig()
                .getServers()
                .stream()
                .filter(server -> server.backupCode().equals(backupCode.backup_code()) || host
                        .equals(server.futureHostname()))
                .toList();
        if (remoteServer.isEmpty()) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } else if (remoteServer.size() > 1) {
            throw new IllegalStateException();
        }

        if (!config.getConfig().updateServerHostname(remoteServer.get(0).hostname(), host)) {
            throw new IllegalStateException();
        }
    }

    /**
     * API Path to migrate a server.
     *
     * @param host   the server making the request
     * @param domain the new domain of the server
     */
    @PUT
    @Path("/server/migrate")
    @Consumes(MediaType.APPLICATION_JSON)
    public void serverMigrate(@HeaderParam("domain") String host, Field.Domain domain) {
        final Config.Server remoteServer = config.getConfig().getServer(host);
        if (remoteServer == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }

        final Config.Server updatedServer = new Config.Server(remoteServer.hostname(), remoteServer
                .oldHostnames(), remoteServer.isVerified(), remoteServer.healthy(), remoteServer
                        .maintenance(), domain.domain(), remoteServer.backupCode());

        if (!config.getConfig().updateServer(updatedServer)) {
            throw new IllegalStateException();
        }
    }

    /**
     * API Path to set maintenance window
     * <p/>
     * Returns 204 if successful. Can also return 404 and 406.
     *
     * @param host        the server making the request
     * @param maintenance maintenance window
     */
    @POST
    @Path("/server/maintenance")
    @Consumes(MediaType.APPLICATION_JSON)
    public void serverMaintenance(@HeaderParam("domain") String host,
            Field.Maintenance maintenance) {
        final Config.Server remoteServer = config.getConfig().getServer(host);
        if (remoteServer == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        if (maintenance.to() - maintenance.from() > 2 * 24 * 60 * 60) {
            throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
        }
        final Config.Server updatedServer = new Config.Server(remoteServer.hostname(), remoteServer
                .oldHostnames(), remoteServer.isVerified(), remoteServer.healthy(),
                new Config.Maintenance(new Date(maintenance.from() * 1000), new Date(maintenance
                        .to() * 1000)), remoteServer.futureHostname(), remoteServer.backupCode());
        if (!config.getConfig().updateServer(updatedServer)) {
            throw new IllegalStateException();
        }
    }

    /**
     * API Path to delete a server
     * <p/>
     * Returns 204 if successful. Can also return 404.
     *
     * @param host the server making the request
     */
    @DELETE
    @Path("/server")
    public void serverDelete(@HeaderParam("domain") String host) {
        final Config.Server remoteServer = config.getConfig().getServer(host);
        if (remoteServer == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        config.getConfig().getExternalBlocks(host).stream().peek(externalBlock -> {
            try {
                if (!data.deleteExternalBlock(externalBlock)) {
                    throw new WebApplicationException("Couldn't delete all Blocks",
                            Response.Status.INTERNAL_SERVER_ERROR);
                }
            } catch (FileNotFoundException ignored) {
            }
        }).forEach(externalBlock -> config.getConfig().deleteExternalBlock(externalBlock.id()));
        config.getConfig()
                .getBlocks()
                .stream()
                .filter(block -> block.serverToId().containsKey(host))
                .forEach(block -> {
                    if (!config.getConfig().removeBlockServer(block.id(), host)) {
                        throw new IllegalStateException();
                    }
                    BackupWorker.BLOCKS_TO_CHECK.add(block);
                });

        config.getConfig().deleteServer(host);
    }

    /**
     * API Path to get the list of saved blocks.
     *
     * @param host the server making the request
     * @return The list of saved blocks. Can also return 404.
     */
    @GET
    @Path("/block")
    @Produces(MediaType.APPLICATION_JSON)
    public Field.BlockList getBlocks(@HeaderParam("domain") String host) {
        final Config.Server remoteServer = config.getConfig().getServer(host);
        if (remoteServer == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }

        final List<Config.ExternalBlock> externalBlocks = config.getConfig()
                .getExternalBlocks(host);
        if (externalBlocks == null) {
            throw new IllegalStateException();
        }
        List<Field.BlockInfo> blockInfos = new ArrayList<>();
        externalBlocks.forEach(externalBlock -> {
            long lastModified = 0;
            try {
                lastModified = data.getLastModified(externalBlock).getEpochSecond();
            } catch (FileNotFoundException ignored) {
            } catch (IOException e) {
                throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
            }
            long finalLastModified = lastModified;
            blockInfos.add(new Field.BlockInfo(externalBlock.id(), finalLastModified));
        });
        return new Field.BlockList(blockInfos);
    }

    /**
     * API Path to reserve an amount of new blocks
     * <p/>
     * Returns 204 if successful. Can also return 404 and 406.
     *
     * @param host   the server making the request
     * @param amount the amount of blocks to reserve
     */
    @POST
    @Path("/block")
    @Consumes(MediaType.APPLICATION_JSON)
    public void reserveBlock(@HeaderParam("domain") String host, Field.Amount amount) {
        Config.Server remoteServer = config.getConfig().getServer(host);
        if (remoteServer == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        if (data.getFreeExternalBlocks() < amount.amount()) {
            throw new WebApplicationException(507);
        }
        final Field.BlockList remoteBlocks = FedService.getFedRestClient(host)
                .getBlocks(config.getConfig().getHostname());
        final FedInfoResponse fedInfo = FedService.getFedRestClient(host)
                .getServerInfo(config.getConfig().getHostname());
        List<Config.ExternalBlock> externalBlocks = config.getConfig().getExternalBlocks(host);
        if (externalBlocks == null) {
            throw new IllegalStateException();
        }
        if (remoteBlocks.blocks().size() * fedInfo.block_size < (externalBlocks.size() + amount
                .amount()) * config.getConfig().getBlockSize()) {
            throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
        }
        for (int i = 0; i < amount.amount(); i++) {
            int counter = 0;
            do {
                counter++;
            } while (counter < 1000 && !config.getConfig()
                    .addNewExternalBlock(new Config.ExternalBlock(UUID.randomUUID().toString(),
                            host)));
            if (counter == 1000) {
                throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
            }
        }
    }

    /**
     * API Path to get a JWT token to access a block on the backup api
     *
     * @param host the server making the request
     * @param id   the id of the block
     * @return The JWT token. Can also return 404.
     */
    @GET
    @Path("/block/{id}/jwt")
    @Produces(MediaType.APPLICATION_JSON)
    public Field.Jwt getBlockJWT(@HeaderParam("domain") String host, @PathParam("id") String id) {

        Config.ExternalBlock externalBlocks = config.getConfig().getExternalBlock(id);
        if (externalBlocks == null || !externalBlocks.serverHostname().equals(host)) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        String jwtToken = jwtUtil.generate(new Field.Hostname(host), 5 * 60);
        config.getConfig().putJwtKey(jwtToken, externalBlocks.id());

        return new Field.Jwt(jwtToken);
    }

    /**
     * API Path to verify a block.
     *
     * @param host        the server making the request
     * @param id          the id of the block
     * @param blockVerify the hash and salt to hash the block
     * @return the calculated hash. Can also return 404 and 406.
     */
    @POST
    @Path("/block/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Field.Hash verifyBlock(@HeaderParam("domain") String host, @PathParam("id") String id,
            Field.BlockVerify blockVerify) throws IOException {

        Config.ExternalBlock externalBlocks = config.getConfig().getExternalBlock(id);
        if (externalBlocks == null || !externalBlocks.serverHostname().equals(host)) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }

        HashMethod hashMethod = HashMethod.getHashMethods()
                .stream()
                .filter(hashMethod1 -> hashMethod1.getHashMethodName()
                        .equalsIgnoreCase(blockVerify.hash_method()))
                .findAny()
                .orElse(null);
        if (hashMethod == null) {
            throw new WebApplicationException(Response.Status.NOT_ACCEPTABLE);
        }

        String calculatedHash;
        try {
            calculatedHash = data.getHash(externalBlocks, Base64.getDecoder()
                    .decode(blockVerify.salt()), hashMethod.getHashFunction());
        } catch (FileNotFoundException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new IOException("could not verify block", e);
        }

        return new Field.Hash(calculatedHash);
    }

    /**
     * API Path to delete a block.
     * <p/>
     * Returns 201 if successful. Can also return 404.
     *
     * @param host the server making the request
     * @param id   the id of the block
     */
    @DELETE
    @Path("/block/{id}")
    public void deleteBlock(@HeaderParam("domain") String host, @PathParam("id") String id) {

        Config.ExternalBlock externalBlocks = config.getConfig().getExternalBlock(id);
        if (externalBlocks == null || !externalBlocks.serverHostname().equals(host)) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }

        try {
            if (!data.deleteExternalBlock(externalBlocks)) {
                throw new WebApplicationException("Couldn't delete block",
                        Response.Status.INTERNAL_SERVER_ERROR);
            }
        } catch (FileNotFoundException e) {
            throw new IllegalStateException(e);
        }
        if (!config.getConfig().deleteExternalBlock(externalBlocks.id())) {
            throw new IllegalStateException();
        }
    }

}
