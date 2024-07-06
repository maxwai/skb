package edu.hm.skb.api.fed;

import edu.hm.skb.config.Config;
import edu.hm.skb.util.model.FedInfoResponse;
import edu.hm.skb.util.model.Field;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

/**
 * Counterpoint to the Federated API. Used to make requests to the Federated API.
 */
@Path("/api/fed/v1")
@RequestScoped
@RegisterRestClient(baseUri = "https://example.com")
public interface FedService {

    /**
     * Generate RestClient with the specified hostname as the baseUri
     *
     * @param hostname the hostname to set
     * @return the rest client
     */
    @NotNull
    static FedService getFedRestClient(String hostname) {
        return QuarkusRestClientBuilder.newBuilder()
                .keyStore(Config.KEYSTORE, Config.KEYSTORE_PASSWORD)
                .baseUri(URI.create("https://" + hostname))
                .build(FedService.class);
    }

    /**
     * API Path to get the server information
     *
     * @param host the server making the request
     * @return the server information
     */
    @GET
    @Path("/server/info")
    @Produces(MediaType.APPLICATION_JSON)
    FedInfoResponse getServerInfo(@HeaderParam("domain") String host);

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
    Field.BackupCode requestVerification(@HeaderParam("domain") String host);

    /**
     * API Path to notify that the server was verified
     *
     * @param host the server making the request
     * @return the Backup Code if successful. Can also return 404.
     */
    @POST
    @Path("/server/verify")
    @Produces(MediaType.APPLICATION_JSON)
    Field.BackupCode acceptVerification(@HeaderParam("domain") String host);

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
    Response serverRestore(@HeaderParam("domain") String host, Field.BackupCode backupCode);

    /**
     * API Path to migrate a server.
     *
     * @param host   the server making the request
     * @param domain the new domain of the server
     */
    @PUT
    @Path("/server/migrate")
    @Consumes(MediaType.APPLICATION_JSON)
    Response serverMigrate(@HeaderParam("domain") String host, Field.Domain domain);

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
    Response serverMaintenance(@HeaderParam("domain") String host, Field.Maintenance maintenance);

    /**
     * API Path to delete a server
     * <p/>
     * Returns 204 if successful. Can also return 404.
     *
     * @param host the server making the request
     */
    @DELETE
    @Path("/server")
    Response serverDelete(@HeaderParam("domain") String host);

    /**
     * API Path to get the list of saved blocks.
     *
     * @param host the server making the request
     * @return The list of saved blocks. Can also return 404.
     */
    @GET
    @Path("/block")
    Field.BlockList getBlocks(@HeaderParam("domain") String host);

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
    Response reserveBlocks(@HeaderParam("domain") String host, Field.Amount amount);

    /**
     * API Path to get a JWT token to access a block on the backup api
     *
     * @param host    the server making the request
     * @param blockId the id of the block
     * @return The JWT token. Can also return 404.
     */
    @GET
    @Path("/block/{blockId}/jwt")
    @Produces(MediaType.APPLICATION_JSON)
    Field.Jwt getBlockJwt(@HeaderParam("domain") String host, @PathParam("blockId") String blockId);

    /**
     * API Path to verify a block.
     *
     * @param host        the server making the request
     * @param blockId     the id of the block
     * @param blockVerify the hash and salt to hash the block
     * @return the calculated hash. Can also return 404 and 406.
     */
    @POST
    @Path("/block/{blockId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Field.Hash verifyBlock(@HeaderParam("domain") String host, @PathParam("blockId") String blockId,
            Field.BlockVerify blockVerify);

    /**
     * API Path to delete a block.
     * <p/>
     * Returns 201 if successful. Can also return 404.
     *
     * @param host    the server making the request
     * @param blockId the id of the block
     */
    @DELETE
    @Path("/block/{blockId}")
    Response deleteBlock(@HeaderParam("domain") String host, @PathParam("blockId") String blockId);
}
