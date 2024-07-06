package edu.hm.skb.api.backup;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

/**
 * Counterpoint to the Backup API. Used to make requests to the Backup API.
 */
@Path("/api/bak/v1")
@ApplicationScoped
@RegisterRestClient(baseUri = "https://example.com")
public interface BackupService {

    /**
     * Generate RestClient with the specified hostname as the baseUri
     *
     * @param hostname the hostname to set
     * @return the rest client
     */
    @NotNull
    static BackupService getBakRestClient(String hostname) {
        return QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create("https://" + hostname))
                .build(BackupService.class);
    }

    /**
     * API Path to upload a new Block.
     *
     * @param token The JWT Token
     * @param id    The id of the block
     * @param data  The block data
     * @return 204 if successful. Can also return 400, 401, 404 and 409.
     */
    @POST
    @Path("/block/{id}")
    @Consumes("application/octet-stream")
    Response blockUpload(@HeaderParam("Authorization") String token, @PathParam("id") String id,
            byte[] data);

    /**
     * API Path to update an already uploaded Block.
     *
     * @param token The JWT Token
     * @param id    The id of the block
     * @param data  The block data
     * @return 204 if successful. Can also return 400, 401 and 404.
     */
    @PUT
    @Path("/block/{id}")
    @Consumes("application/octet-stream")
    Response blockUpdate(@HeaderParam("Authorization") String token, @PathParam("id") String id,
            byte[] data);

    /**
     * API Path to get back the content of a block
     *
     * @param token The JWT Token
     * @param id    The id of the block
     * @return The Block as an octet-steam. Can also return 400, 401 and 404.
     */
    @GET
    @Path("/block/{id}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    Response blockDownload(@HeaderParam("Authorization") String token, @PathParam("id") String id);
}
