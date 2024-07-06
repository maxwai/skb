package edu.hm.skb.util.model;

import java.util.List;
import java.util.Objects;

/**
 * The Client Server Info
 */
@SuppressWarnings("PMD.FieldNamingConventions")
public class ClientServerInfo extends BaseServerInfo {
    /**
     * List of old hostnames
     */
    public List<String> old_hostnames;
    /**
     * Amount of used blocks
     */
    public int used_blocks;
    /**
     * Remote has confirmed this server
     */
    public boolean is_confirmed;
    /**
     * If the server is healthy
     */
    public boolean healthy;

    /**
     * Create Client Server Info
     *
     * @param fedInfo       The server Info
     * @param hostname      hostname of the server
     * @param old_hostnames List of old hostnames
     * @param is_verified   if the server is verified
     * @param healthy       If the server is healthy
     * @param used_blocks   Amount of used blocks
     */
    @SuppressWarnings({"PMD.FormalParameterNamingConventions"})
    public ClientServerInfo (FedInfoResponse fedInfo, String hostname, List<String> old_hostnames,
            boolean is_verified, boolean healthy, int used_blocks) {
        super(fedInfo);
        this.old_hostnames = old_hostnames;
        this.used_blocks = used_blocks;
        this.is_confirmed = fedInfo.is_verified;
        this.healthy = healthy;

        this.hostname = hostname;
        this.is_verified = is_verified;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClientServerInfo that)) {
            return false;
        }
        return super.equals(o) && Objects.equals(old_hostnames, that.old_hostnames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), old_hostnames);
    }
}
