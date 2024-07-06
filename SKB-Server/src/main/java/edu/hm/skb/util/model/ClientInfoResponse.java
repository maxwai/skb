package edu.hm.skb.util.model;

import java.util.List;
import java.util.Objects;

/**
 * The Client Info Response
 */
@SuppressWarnings({"PMD.FieldNamingConventions", "PMD.LongVariable"})
public class ClientInfoResponse {
    /**
     * Disk size divided by 3 in bytes
     */
    public long total_usage_size;
    /**
     * Amount of own data stored on this server in bytes
     */
    public long used_data;
    /**
     * Blocks that aren't secured on any other servers
     */
    public long data_unsecured;
    /**
     * Blocks that are only secured on one server
     */
    public long data_secured;
    /**
     * Blocks that are secured on at least 2 other servers
     */
    public long data_safely_secured;
    /**
     * List of known Servers
     */
    public List<ClientServerInfo> servers;
    /**
     * List of saved Files
     */
    public List<Field.FileInfo> files;

    /**
     * Create Client Info
     *
     * @param total_usage_size    Disk size divided by 3 in bytes
     * @param used_data           Amount of own data stored on this server in bytes
     * @param data_unsecured      Blocks that aren't secured on any other servers
     * @param data_secured        Blocks that are only secured on one server
     * @param data_safely_secured Blocks that are secured on at least 2 other servers
     * @param servers             List of known Servers
     * @param files               List of saved Files
     */
    @SuppressWarnings({"PMD.FormalParameterNamingConventions"})
    public ClientInfoResponse (long total_usage_size, long used_data, long data_unsecured,
            long data_secured, long data_safely_secured, List<ClientServerInfo> servers,
            List<Field.FileInfo> files) {
        this.total_usage_size = total_usage_size;
        this.used_data = used_data;
        this.data_unsecured = data_unsecured;
        this.data_secured = data_secured;
        this.data_safely_secured = data_safely_secured;
        this.servers = servers;
        this.files = files;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClientInfoResponse that)) {
            return false;
        }
        boolean result = total_usage_size == that.total_usage_size;
        result &= used_data == that.used_data;
        result &= data_unsecured == that.data_unsecured;
        result &= data_secured == that.data_secured;
        result &= data_safely_secured == that.data_safely_secured;
        result &= Objects.equals(servers, that.servers);
        result &= Objects.equals(files, that.files);
        return result;
    }

    @Override
    public int hashCode() {
        return Objects.hash(total_usage_size, used_data, data_unsecured, data_secured,
                data_safely_secured);
    }
}
