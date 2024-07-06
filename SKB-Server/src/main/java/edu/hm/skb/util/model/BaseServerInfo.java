package edu.hm.skb.util.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * The Base Server Info
 */
@SuppressWarnings({"PMD.FieldNamingConventions", "PMD.LongVariable"})
public class BaseServerInfo {
    /**
     * Server Hostname
     */
    public String hostname;
    /**
     * Owner
     */
    public String owner;
    /**
     * if the server is verified on this server
     */
    public boolean is_verified;
    /**
     * List of supported Hash Methods
     */
    public List<String> hash_methods;
    /**
     * Block size in bytes
     */
    public long block_size;
    /**
     * Free blocks
     */
    public int free_blocks;
    /**
     * Percent of blocks checked on every healthcheck
     */
    public int healthcheck_percent;
    /**
     * The interval in minutes where the healthcheck is made
     */
    public int healthcheck_interval;

    /**
     * Create Base Server Info
     *
     * @param hostname             Server Hostname
     * @param owner                Owner
     * @param is_verified          if the server is verified on this server
     * @param hash_methods         List of supported Hash Methods
     * @param block_size           Block size in bytes
     * @param free_blocks          Free blocks
     * @param healthcheck_percent  Percent of blocks checked on every healthcheck
     * @param healthcheck_interval The interval in minutes where the healthcheck is made
     */
    @SuppressWarnings({"PMD.FormalParameterNamingConventions"})
    public BaseServerInfo (@JsonProperty("hostname") String hostname,
            @JsonProperty("owner") String owner, @JsonProperty("is_verified") boolean is_verified,
            @JsonProperty("hash_methods") List<String> hash_methods,
            @JsonProperty("block_size") long block_size,
            @JsonProperty("free_blocks") int free_blocks,
            @JsonProperty("healthcheck_percent") int healthcheck_percent,
            @JsonProperty("healthcheck_interval") int healthcheck_interval) {
        this.hostname = hostname;
        this.owner = owner;
        this.is_verified = is_verified;
        this.hash_methods = hash_methods;
        this.block_size = block_size;
        this.free_blocks = free_blocks;
        this.healthcheck_percent = healthcheck_percent;
        this.healthcheck_interval = healthcheck_interval;
    }

    /**
     * Make a Copy of a BaseServerInfo
     *
     * @param baseServerInfo baseServerInfo to copy
     */
    public BaseServerInfo (BaseServerInfo baseServerInfo) {
        this.hostname = baseServerInfo.hostname;
        this.owner = baseServerInfo.owner;
        this.is_verified = baseServerInfo.is_verified;
        this.hash_methods = baseServerInfo.hash_methods;
        this.block_size = baseServerInfo.block_size;
        this.free_blocks = baseServerInfo.free_blocks;
        this.healthcheck_percent = baseServerInfo.healthcheck_percent;
        this.healthcheck_interval = baseServerInfo.healthcheck_interval;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof BaseServerInfo that && Objects.equals(hostname,
                that.hostname));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(hostname);
    }

    @Override
    public String toString() {
        return "BaseServerInfo{" + "hostname='" + hostname + '\'' + ", owner='" + owner + '\'' + ", is_verified=" + is_verified + ", hash_methods=" + hash_methods + ", block_size=" + block_size + ", free_blocks=" + free_blocks + ", healthcheck_percent=" + healthcheck_percent + ", healthcheck_interval=" + healthcheck_interval + '}';
    }
}
