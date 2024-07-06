package edu.hm.skb.api.client;

import edu.hm.skb.api.fed.FedService;
import edu.hm.skb.api.security.SkbCheckSignature;
import edu.hm.skb.config.Config;
import edu.hm.skb.config.ConfigInjector;
import edu.hm.skb.data.Data;
import edu.hm.skb.util.WordListBean;
import edu.hm.skb.util.hash.HashMethod;
import edu.hm.skb.util.model.ClientInfoResponse;
import edu.hm.skb.util.model.ClientServerInfo;
import edu.hm.skb.util.model.FedInfoResponse;
import edu.hm.skb.util.model.BaseServerInfo;
import edu.hm.skb.util.model.Field;
import edu.hm.skb.worker.BackupWorker;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.common.util.DateUtil;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.BiPredicate;

/**
 * API Paths for the Client REST API
 * <p/>
 * Every request is authenticated with the Signature Header.
 */
@Path("/api/client/v1")
@SkbCheckSignature
public class ClientResource {

    /**
     * The log instance
     */
    @NotNull
    private static final Logger LOG = Logger.getLogger(ClientResource.class);

    /**
     * Max retries in a try loop
     */
    private static final int MAX_RETRIES = 10;

    /**
     * Parser for the Last modified date gotten in the header
     */
    private static final SimpleDateFormat LAST_MODIFIED_PARSER = new SimpleDateFormat(
            "EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);

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
     * Helper method to get the size of all blocks that are secured on at least the given amount of
     * servers
     *
     * @param serverCount the amount to check
     * @param comparison  The comparison function. The serverCount field will be set as the second
     *                    parameter
     * @return the size of blocks that match the predicate in bytes
     */
    private long getFilteredBlockSize(int serverCount, BiPredicate<Integer, Integer> comparison) {
        return config.getConfig()
                .getBlocks()
                .stream()
                .filter(block -> comparison.test(block.serverToId().size(), serverCount))
                // TODO: don't use block size but extract actual data size in block
                .mapToLong(block -> config.getConfig().getBlockSize())
                .sum();
    }

    /**
     * Skips files that couldn't be resolved because of either:
     * <ul>
     * <li>the file is null</li>
     * <li>the data instance is null</li>
     * <li>the last modified date couldn't be resolved</li>
     * </ul>
     *
     * @param files the list of files to check
     * @return list of file infos
     */
    @NotNull
    private List<Field.FileInfo> toFileInfo(@NotNull List<Config.File> files) {
        if (files.isEmpty()) {
            return Collections.emptyList();
        }
        return files.stream().map(file -> {
            if (file == null) {
                return null;
            }
            try {
                return new Field.FileInfo(file.id(), file.path(), data.getLastModified(file)
                        .getEpochSecond());
            } catch (IOException e) {
                return null;
            }
        }).toList().stream().filter(Objects::nonNull).toList();
    }

    /**
     * Tries to get the file with a fileId
     *
     * @param fileId the fileId to get the file for
     * @return on Optional containing the found file if any
     */
    @NotNull
    private Optional<Config.File> getFileById(@NotNull String fileId) {
        return config.getConfig()
                .getFiles()
                .stream()
                .filter(file -> file.id().equals(fileId))
                .findFirst();
    }

    /**
     * API Path to get the server info for a client
     *
     * @return the server info
     */
    @GET
    @Path("/info")
    @Produces(MediaType.APPLICATION_JSON)
    public ClientInfoResponse clientGetInfo() {
        // remember: numeric values are in bytes
        final List<ClientServerInfo> servers = new ArrayList<>();
        for (Config.Server server1 : config.getConfig().getServers()) {
            final FedInfoResponse fedInfo = FedService.getFedRestClient(server1.hostname())
                    .getServerInfo(config.getConfig().getHostname());
            List<Config.ExternalBlock> blocks = config.getConfig()
                    .getExternalBlocks(server1.hostname());

            ClientServerInfo apply = new ClientServerInfo(fedInfo, server1.hostname(), server1
                    .oldHostnames(), server1.isVerified(), server1.healthy(), blocks.size());
            servers.add(apply);
        }

        return new ClientInfoResponse(data.getTotalSize(), data.getUsedSize(), getFilteredBlockSize(
                0, Integer::equals), getFilteredBlockSize(1, Integer::equals), getFilteredBlockSize(
                        2, (a, b) -> a >= b), servers, toFileInfo(config.getConfig().getFiles()));
    }

    /**
     * API Path to add a new file to the config.getConfig().
     *
     * @param path The path (on the client machine) of the new file
     * @return The uuid of the file
     */
    @POST
    @Path("/file")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Field.Uuid64 clientFileAdd(Field.Path path) {
        // initial id is based on path
        Field.Uuid64 id = new Field.Uuid64(UUID.randomUUID().toString());
        // retry until unique
        int retries = 0;
        while (retries <= MAX_RETRIES && !config.getConfig()
                .addNewFile(new Config.File(id.id(), path.path()))) {
            id = new Field.Uuid64(UUID.randomUUID().toString());
            retries++;
        }
        if (retries > MAX_RETRIES) {
            throw new WebApplicationException("Error creating file: too many retries",
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
        return id;
    }

    /**
     * API Path to upload a new file
     * <p/>
     * Returns 204 if successful. Can also return 400, 404 and 409.
     *
     * @param id           the id of the file
     * @param lastModified the lastModified information
     * @param stream       The file data
     */
    @POST
    @Path("/file/{id}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    public void clientFileUpload(@PathParam("id") String id,
            @HeaderParam("Last-Modified") String lastModified, InputStream stream) {
        final Optional<Config.File> file = getFileById(id);
        if (file.isEmpty()) {
            throw new WebApplicationException("File not found", Response.Status.NOT_FOUND);
        }
        List<Config.Block> changedBlocks;
        try {
            synchronized (LAST_MODIFIED_PARSER) {
                changedBlocks = data.createFile(stream, file.get(), LAST_MODIFIED_PARSER.parse(
                        lastModified).toInstant());
            }
            if (!changedBlocks.isEmpty()) {
                //noinspection UseBulkOperation to be thread safe
                changedBlocks.forEach(BackupWorker.BLOCKS_TO_CHECK::add);
            }
        } catch (FileAlreadyExistsException ignored) {
            throw new WebApplicationException("File already exists", Response.Status.CONFLICT);
        } catch (DateUtil.DateParseException ignored) {
            throw new WebApplicationException("lastModified Date can't be pared",
                    Response.Status.BAD_REQUEST);
        } catch (Exception e) {
            throw new WebApplicationException("Error uploading file", e,
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
        if (changedBlocks.isEmpty()) {
            throw new WebApplicationException("File couldn't be created",
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * API Path to download a file
     *
     * @param id the id of the file
     * @return octet stream of a file. Can also return 404.
     */
    @GET
    @Path("/file/{id}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response clientFileDownload(@PathParam("id") String id) {
        final Optional<Config.File> file = getFileById(id);
        if (file.isEmpty()) {
            throw new WebApplicationException("File not found", Response.Status.NOT_FOUND);
        }
        InputStream stream = null;
        try {
            stream = data.getFile(file.get());
            return Response.ok(stream, MediaType.APPLICATION_OCTET_STREAM)
                    .header("content-disposition", "attachment; filename = " + file.get().path())
                    .build();
        } catch (Exception e) {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                }
            }
            throw new WebApplicationException("Error downloading file", e,
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * API Path to upload an updated file
     * <p/>
     * Returns 204 if successful. Can also return 400, 404 and 409.
     *
     * @param id           the id of the file
     * @param lastModified the lastModified information
     * @param stream       The file data
     */
    @PUT
    @Path("/file/{id}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    public void clientFileReplace(@PathParam("id") String id,
            @HeaderParam("Last-Modified") String lastModified, InputStream stream) {
        final Optional<Config.File> file = getFileById(id);
        if (file.isEmpty()) {
            throw new WebApplicationException("File not found", Response.Status.NOT_FOUND);
        }
        try {
            synchronized (LAST_MODIFIED_PARSER) {
                //noinspection UseBulkOperation to be thread safe
                data.updateFile(stream, file.get(), LAST_MODIFIED_PARSER.parse(lastModified)
                        .toInstant()).forEach(BackupWorker.BLOCKS_TO_CHECK::add);
            }
        } catch (DateUtil.DateParseException ignored) {
            throw new WebApplicationException("lastModified Date can't be pared",
                    Response.Status.BAD_REQUEST);
        } catch (Exception e) {
            throw new WebApplicationException("Error updating file", e,
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * API Path to delete a file from the server
     * <p/>
     * Returns 204 if successful. Can also return 404.
     *
     * @param id the file id
     */
    @DELETE
    @Path("/file/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public void clientFileRemove(@PathParam("id") String id) {
        final Optional<Config.File> file = getFileById(id);
        if (file.isEmpty()) {
            throw new WebApplicationException("File not found", Response.Status.NOT_FOUND);
        }
        config.getConfig().deleteFile(id);
        try {
            //noinspection UseBulkOperation to be thread safe
            data.deleteFile(file.get()).forEach(BackupWorker.BLOCKS_TO_CHECK::add);
        } catch (Exception e) {
            throw new WebApplicationException("Error deleting file", e,
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * API Path to add a new remote server.
     *
     * @param hostname The hostname of the server
     * @return The Backup Code. Can also return 406 and 409.
     */
    @POST
    @Path("/server")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Field.BackupCode clientServerAdd(@QueryParam("hostname") String hostname) {
        List<String> serverHashMethods = FedService.getFedRestClient(hostname)
                .getServerInfo(config.getConfig().getHostname()).hash_methods;
        if (HashMethod.getHashMethods()
                .stream()
                .map(HashMethod::getHashMethodName)
                .noneMatch(serverHashMethods::contains)) {
            throw new WebApplicationException("Server has no common hash Method, can't add",
                    Response.Status.NOT_ACCEPTABLE);
        }
        String backupCode = wordListBean.generateBackupCode(config.getConfig(), 5);
        if (backupCode == null) {
            throw new WebApplicationException("Backup Code couldn't be generated");
        }
        final Field.BackupCode code = FedService.getFedRestClient(hostname)
                .requestVerification(config.getConfig().getHostname());
        final Config.Server s = new Config.Server(hostname, List.of(), true, true, null, null,
                backupCode);
        if (!config.getConfig().addNewServer(s)) {
            throw new WebApplicationException("Adding server failed", Response.Status.CONFLICT);
        }
        return code;
    }

    /**
     * API Path to make a server discovery with the given depth
     *
     * @param depth the depth to search for. A depth of one only searches on the known server for
     *              other servers
     * @return The list of found servers
     */
    @GET
    @Path("/server")
    @Produces(MediaType.APPLICATION_JSON)
    public Field.ServerList clientServerScan(@QueryParam("depth") int depth) {
        final Set<String> scannedHosts = new HashSet<>(List.of(config.getConfig().getHostname()));
        final Set<BaseServerInfo> result = new HashSet<>();

        final Set<String> hostsToScan = new HashSet<>(config.getConfig()
                .getServers()
                .stream()
                .map(Config.Server::hostname)
                .toList());
        int depthCount = 0;
        while (depthCount < depth) { // BFS
            Set<String> currentDepthHosts = new HashSet<>(hostsToScan);
            scannedHosts.addAll(hostsToScan);
            hostsToScan.clear();

            for (String hostname : currentDepthHosts) {
                final FedInfoResponse fedInfo = FedService.getFedRestClient(hostname)
                        .getServerInfo(config.getConfig().getHostname());
                if (fedInfo == null) {
                    continue;
                }
                fedInfo.known_server.stream()
                        .filter(s -> !scannedHosts.contains(s))
                        .forEach(hostsToScan::add);

                result.add(fedInfo);
                scannedHosts.add(hostname);
            }
            depthCount++;
        }
        return new Field.ServerList(new ArrayList<>(result));
    }


    /**
     * API Path to accept a pending server
     *
     * @param hostname the hostname of the server
     * @return the backup code. Can also return 406.
     */
    @PUT
    @Path("/server")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Field.BackupCode clientServerAccept(@QueryParam("hostname") String hostname) {
        final Config.Server s = config.getConfig().getServer(hostname);
        if (s == null) {
            throw new WebApplicationException("Server with given hostname not found",
                    Response.Status.NOT_FOUND);
        }

        List<String> serverHashMethods = FedService.getFedRestClient(hostname)
                .getServerInfo(config.getConfig().getHostname()).hash_methods;
        if (HashMethod.getHashMethods()
                .stream()
                .map(HashMethod::getHashMethodName)
                .noneMatch(serverHashMethods::contains)) {
            throw new WebApplicationException("Server has no common hash Method, can't add",
                    Response.Status.NOT_ACCEPTABLE);
        }
        final Field.BackupCode code = FedService.getFedRestClient(hostname)
                .acceptVerification(config.getConfig().getHostname());
        if (code == null || code.backup_code().isBlank()) {
            throw new WebApplicationException("Accepting server failed",
                    Response.Status.INTERNAL_SERVER_ERROR);
        }

        final boolean success = config.getConfig()
                .updateServer(new Config.Server(s.hostname(), s.oldHostnames(), true, s.healthy(), s
                        .maintenance(), s.futureHostname(), code.backup_code()));
        if (!success) {
            throw new WebApplicationException("Updating server failed",
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
        return code;
    }

    /**
     * API Path to delete a remote server
     * <p/>
     * Returns 204 if successful. Can also return 404.
     *
     * @param hostname the hostname of the server
     */
    @DELETE
    @Path("/server")
    @Produces(MediaType.APPLICATION_JSON)
    public void clientServerRemove(@QueryParam("hostname") String hostname) {
        try (Response res = FedService.getFedRestClient(hostname)
                .serverDelete(config.getConfig().getHostname())) {
            if (res.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                throw new WebApplicationException(res);
            }
        }
        boolean success = config.getConfig().deleteServer(hostname);
        config.getConfig().getExternalBlocks(hostname).stream().peek(externalBlock -> {
            try {
                if (!data.deleteExternalBlock(externalBlock)) {
                    LOG.warnf("Couldn't delete external block {0}", externalBlock.id());
                }
            } catch (FileNotFoundException ignored) {
            }
        }).forEach(externalBlock -> config.getConfig().deleteExternalBlock(externalBlock.id()));
        config.getConfig()
                .getBlocks()
                .stream()
                .filter(block -> block.serverToId().containsKey(hostname))
                .forEach(block -> config.getConfig().removeBlockServer(block.id(), hostname));
        if (!success) {
            throw new WebApplicationException("Server not found", Response.Status.NOT_FOUND);
        }
    }
}
