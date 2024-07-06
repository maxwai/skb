package edu.hm.skb.api.backup;

import edu.hm.skb.config.Config;
import edu.hm.skb.config.ConfigInjector;
import edu.hm.skb.data.Data;
import edu.hm.skb.util.JwtUtil;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;

/**
 * API Paths for the Backup REST API
 */
@Path("/api/bak/v1")
public class BackupResource {

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
     * JWT Management Instance
     */
    @Inject
    /* default */ JwtUtil jwt;

    /**
     * API Path to upload a new Block.
     * <p/>
     * Returns 204 if successful. Can also return 400, 401, 404 and 409.
     *
     * @param token  The JWT Token
     * @param isSize The Size in bytes of the block
     * @param id     The id of the block
     * @param is     The block data
     */
    @POST
    @Path("/block/{id}")
    @Consumes("application/octet-stream")
    public void blockUpload(@HeaderParam("Authorization") String token,
            @HeaderParam("Content-Length") long isSize, @PathParam("id") String id,
            InputStream is) {
        blockSizeCheck(isSize); // throws WebApplicationException (BAD_REQUEST)
        final Config.ExternalBlock block = validateAndGetBlock(token, id);
        try {
            if (!data.createExternalBlock(is, block)) {
                throw new IllegalStateException("Block creation failed");
            }
        } catch (FileAlreadyExistsException e) {
            throw new WebApplicationException(e, Response.Status.CONFLICT);
        }
    }

    /**
     * API Path to update an already uploaded Block.
     * <p/>
     * Returns 204 if successful. Can also return 400, 401 and 404.
     *
     * @param token  The JWT Token
     * @param isSize The Size in bytes of the block
     * @param id     The id of the block
     * @param is     The block data
     */
    @PUT
    @Path("/block/{id}")
    @Consumes("application/octet-stream")
    public void blockUpdate(@HeaderParam("Authorization") String token,
            @HeaderParam("Content-Length") long isSize, @PathParam("id") String id,
            InputStream is) {
        blockSizeCheck(isSize);
        final Config.ExternalBlock block = validateAndGetBlock(token, id);
        try {
            if (!data.updateExternalBlock(is, block)) {
                throw new IllegalStateException("Block update failed");
            }
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Block not found on disk", e);
        }
    }

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
    public Response blockDownload(@HeaderParam("Authorization") String token,
            @PathParam("id") String id) {
        final Config.ExternalBlock block = validateAndGetBlock(token, id);
        InputStream is;
        try {
            is = data.getExternalBlock(block);
            return Response.ok(is, MediaType.APPLICATION_OCTET_STREAM)
                    .header("Authorization", token)
                    .build();
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Block not found on disk", e);
        } catch (IOException e) {
            throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Checks if the size of a block matches the expected size.
     *
     * @param size the reported size of the block
     * @throws WebApplicationException with BAD_REQUEST (400)
     */
    private void blockSizeCheck(long size) {
        if (size <= 0) {
            throw new WebApplicationException("Block size missing or invalid",
                    Response.Status.BAD_REQUEST);
        }
        if (size != config.getConfig().getBlockSize()) {
            throw new WebApplicationException("Block size mismatch", Response.Status.BAD_REQUEST);
        }
    }

    /**
     * Checks the JWT Token and retrieves the External Block Instance
     *
     * @param token The JWT token
     * @param id    The id of the Block
     * @return The found Block
     *
     * @throws WebApplicationException with BAD_REQUEST (400), UNAUTHORIZED (401) or NOT_FOUND (404)
     */
    @NotNull
    private Config.ExternalBlock validateAndGetBlock(@Nullable String token, @Nullable String id) {
        if (id == null || id.isEmpty()) {
            throw new WebApplicationException("Block id missing", Response.Status.BAD_REQUEST);
        }
        final Config.ExternalBlock block = config.getConfig().getExternalBlock(id);
        if (block == null) {
            throw new WebApplicationException("Block not found in config",
                    Response.Status.NOT_FOUND);
        }
        if (token == null || token.isEmpty()) {
            throw new WebApplicationException("Token missing", Response.Status.UNAUTHORIZED);
        }
        final String storedToken = config.getConfig().getJwtKey(id);
        if (storedToken == null || storedToken.isEmpty()) {
            throw new WebApplicationException("No token stored for this id",
                    Response.Status.UNAUTHORIZED);
        }
        if (!token.equals(storedToken)) {
            throw new WebApplicationException("Token does not match stored token",
                    Response.Status.UNAUTHORIZED);
        }
        if (!jwt.validate(token)) {
            config.getConfig().deleteJwtKey(id);
            throw new WebApplicationException("Token validation failed",
                    Response.Status.UNAUTHORIZED);
        }
        return block;
    }
}

