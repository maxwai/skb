package edu.hm.skb.util.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Fed Info Response
 */
@SuppressWarnings("PMD.FieldNamingConventions")
public class FedInfoResponse extends BaseServerInfo {
    /**
     * Known Servers
     */
    public List<String> known_server;

    /**
     * @param hostname             Server Hostname
     * @param owner                Owner
     * @param is_verified          if the server is verified on this server
     * @param hash_methods         List of supported Hash Methods
     * @param block_size           Block size in bytes
     * @param free_blocks          Free blocks
     * @param healthcheck_percent  Percent of blocks checked on every healthcheck
     * @param healthcheck_interval The interval in minutes where the healthcheck is made
     * @param known_server         known servers
     */
    @SuppressWarnings({"PMD.FormalParameterNamingConventions"})
    public FedInfoResponse (@JsonProperty("hostname") String hostname,
            @JsonProperty("owner") String owner, @JsonProperty("block_size") long block_size,
            @JsonProperty("free_blocks") int free_blocks,
            @JsonProperty("healthcheck_percent") int healthcheck_percent,
            @JsonProperty("healthcheck_interval") int healthcheck_interval,
            @JsonProperty("hash_methods") List<String> hash_methods,
            @JsonProperty("is_verified") boolean is_verified,

            @JsonProperty("known_server") List<String> known_server) {
        super(hostname, owner, is_verified, hash_methods, block_size, free_blocks,
                healthcheck_percent, healthcheck_interval);
        this.known_server = known_server;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FedInfoResponse that)) {
            return false;
        }
        return super.equals(o) && Objects.equals(known_server, that.known_server);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), known_server);
    }

    @Override
    public String toString() {
        return "FedInfoResponse{" + "known_server=" + known_server + "} " + super.toString();
    }
}
