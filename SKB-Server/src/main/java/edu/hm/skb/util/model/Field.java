package edu.hm.skb.util.model;

import java.util.List;

/**
 * Collection of simple fields used in responses
 */
public interface Field {

    /* ##### Simple Models ##### */

    record Amount(int amount) {}


    record ApiPath(String api_path) {}


    record BackupCode(String backup_code) {}


    record Domain(String domain) {}


    record Hash(String hash) {}


    record Hostname(String hostname) {}


    record Nonce(String nonce) {}


    record Path(String path) {}


    record Jwt(String jwt) {}


    record Uuid64(String id) {}

    /* ##### Complex Models ##### */


    /**
     * @param id            uuid64
     * @param last_modified unix timestamp in seconds
     */
    record BlockInfo(String id, long last_modified) {}


    record BlockList(List<BlockInfo> blocks) {}


    /**
     * @param hash_method hash method
     * @param salt        base64-salt
     */
    record BlockVerify(String hash_method, String salt) {}


    /**
     * @param id            uuid64
     * @param path          file path
     * @param last_modified unix timestamp in seconds
     */
    record FileInfo(String id, String path, long last_modified) {}


    /**
     * @param from unix timestamp in seconds
     * @param to   unix timestamp in seconds
     */
    record Maintenance(long from, long to) {}


    /**
     * @param hash_method hash method
     * @param nonce       base64-nonce
     */
    record NewBlock(String hash_method, String nonce) {}


    record ServerList(List<BaseServerInfo> servers) {}
}
