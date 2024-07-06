//! # Data Models
//!
//! Models to bridge between Rust datatypes and the JSON bodys of the REST requests

use serde::{Deserialize, Serialize};

#[doc(hidden)]
mod test;

/// The root JSON object that we get from the `GET /info` API path
#[derive(Deserialize, Debug, PartialEq, Eq)]
pub struct InfoRoot {
    /// The total available space for backup in bytes
    pub total_usage_size: u64,
    /// The space used on the server for backups in bytes
    pub used_data: u64,
    /// The amount of data in bytes that is not secured on any remote server
    pub data_unsecured: u64,
    /// The amount of data in bytes that is only secured on one remote server
    pub data_secured: u64,
    /// The amount of data in bytes that is secured on two remote servers
    pub data_safely_secured: u64,
    /// The list of added servers
    pub servers: Vec<InfoServer>,
    /// The list of files saved on the server
    pub files: Vec<InfoFile>,
}

/// The server object saved in the [InfoRoot::servers] field of the [InfoRoot] struct
#[derive(Deserialize, Debug, PartialEq, Eq)]
pub struct InfoServer {
    /// The hostname of the server
    pub hostname: String,
    /// Collection of known old hostnames for the server
    pub old_hostnames: Vec<String>,
    /// The name of the owner of the server
    pub owner: String,
    /// The size of blocks on the server in bytes
    pub block_size: u64,
    /// The amount of free blocks on the server
    pub free_blocks: u64,
    /// The amount of blocks used by this server on the remote server
    pub used_blocks: u64,
    /// The portion of blocks in percent that are checked on every healthcheck
    pub healthcheck_percent: u8,
    /// The interval in minutes where healthchecks are done
    pub healthcheck_interval: u64,
    /// If this server is verified on our server
    pub is_verified: bool,
    /// If this server has verified our server
    pub is_confirmed: bool,
    /// If this server is healthy
    pub healthy: bool,
}

/// The server object saved in the [InfoRoot::files] field of the [InfoRoot] struct
#[derive(Deserialize, Debug, PartialEq, Eq)]
pub struct InfoFile {
    /// The internal uuid64 of the file on the server
    pub id: String,
    /// The local path of the file on the machine
    pub path: String,
    /// The unix timestamp in seconds of the last modified timestamp of the file on the server
    pub last_modified: i64,
}

/// The root JSON object that we get from the `POST /file` API path
#[derive(Deserialize, Debug, PartialEq, Eq)]
pub struct FilePostRoot {
    /// The internal uuid64 of the file on the server
    pub id: String,
}

/// The root JSON object that we get from the `GET /server` API path
#[derive(Deserialize, Debug, PartialEq, Eq)]
pub struct ServerRoot {
    /// The list of discovered servers that are not added via the `POST /server` API path
    pub servers: Vec<ServerItem>,
}

/// The server object saved in the [ServerRoot::servers] field of the [ServerRoot] struct
#[derive(Deserialize, Debug, PartialEq, Eq)]
pub struct ServerItem {
    /// The hostname of the server
    pub hostname: String,
    /// The name of the owner of the server
    pub owner: String,
    /// The size of blocks on the server in bytes
    pub block_size: u64,
    /// The amount of free blocks on the server
    pub free_blocks: u64,
    /// The portion of blocks in percent that are checked on every healthcheck
    pub healthcheck_percent: u8,
    /// The interval in minutes where healthchecks are done
    pub healthcheck_interval: u64,
    /// The list of hash methods that the remote server supports (reduced to those that our server
    /// also supports)
    pub hash_methods: Vec<String>,
}

/// The root JSON object that we get from the `POST /server` or `PUT /server` API path
#[derive(Deserialize, Debug, PartialEq, Eq)]
pub struct NewServerRoot {
    /// The Backup code that needs to be saved somewhere
    pub backup_code: String,
}

/// The root JSON object that we send on a request when the body is otherwise empty
#[derive(Serialize, Debug, PartialEq, Eq)]
pub struct EmptyBody {
    /// Nonce for the signature hash
    pub nonce: String,
}

/// the root JSON object that we send on the `POST /file` API path
#[derive(Serialize, Debug, PartialEq, Eq)]
pub struct PostFileBody {
    /// The local path of the file on the machine
    pub path: String,
    /// Nonce for the signature hash
    pub nonce: String,
}
