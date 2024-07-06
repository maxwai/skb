//! # Env Module
//!
//! Collection of configuration variables that are initialized the first time they are accessed

use std::path::PathBuf;
use std::process::exit;
use std::sync::OnceLock;
use std::{env, fs};

use clap::{Parser, Subcommand};
use log::{debug, error, info};
use openssl::pkey::Private;
use openssl::rsa::Rsa;
use reqwest::Url;
use serde::Deserialize;

/// Command line args
static CLI_ARGS: OnceLock<Args> = OnceLock::new();
/// JSON Config file information
static JSON_CONFIG: OnceLock<JsonConfig> = OnceLock::new();
/// RSA private key to authenticate on the server
static PRIVATE_KEY: OnceLock<Rsa<Private>> = OnceLock::new();
/// API URL
static API_URL: OnceLock<Url> = OnceLock::new();

/// Command line argument struct
#[derive(Parser, Debug, Clone)]
#[command(version, about = "SKB CLI program", long_about = None)]
pub struct Args {
    #[command(subcommand)]
    pub command: Commands,
    /// Verbose level, up to 3 levels
    #[arg(short, long, action = clap::ArgAction::Count, default_value_t = 0)]
    pub verbose: u8,
    /// Allow self-signed certificate
    #[arg(short='k', long, default_value_t = false)]
    pub allow_unsafe: bool,
}

#[derive(Subcommand, Debug, Clone)]
pub enum Commands {
    /// Print the server status
    Status,
    /// Server operations
    Server {
        #[command(subcommand)]
        command: ServerCommands,
    },
    /// File operations
    File {
        #[command(subcommand)]
        command: FileCommands,
    },
}

#[derive(Subcommand, Debug, Clone)]
pub enum ServerCommands {
    /// List connected servers in detail
    List,
    /// Discover new servers
    Discover {
        /// Depth with which to search for servers
        depth: Option<u8>,
    },
    /// Verify/Confirm a remote server
    Verify {
        /// Hostname of remote server
        hostname: String,
    },
    /// Add a new server, server will need to verify you first before you can use it.
    New {
        /// Hostname of remote server
        hostname: String,
    },
    /// Delete a server. Blocks saved on the server will be deleted
    Delete {
        /// Hostname of remote server
        hostname: String,
    },
}

#[derive(Subcommand, Debug, Clone)]
pub enum FileCommands {
    /// List saved files in detail
    List,
    /// Add new file to sync on the server
    Add {
        /// The file to add
        file: PathBuf,
    },
    /// Update file on the server
    Update {
        /// The file to update
        file: PathBuf,
        /// Force updating file even if file on server is newer
        #[arg(short, long, default_value_t = false)]
        force: bool,
    },
    /// Download file from the server
    Download {
        /// The file to download
        file: PathBuf,
        /// Force download file even if local file is newer
        #[arg(short, long, default_value_t = false)]
        force: bool,
    },
    /// Delete file from server
    Delete {
        /// The file to delete on server (not locally)
        file: PathBuf,
    },
    /// Sync every file to the newest version (Both directions)
    Sync,
}

/// The root JSON object of the `config.json` file
#[derive(Deserialize, Debug, PartialEq, Eq, Clone)]
pub struct JsonConfig {
    url: String,
}

/// Get the CLI Arguments
pub fn get_cli_args() -> Args {
    CLI_ARGS.get_or_init(Args::parse).clone()
}

/// Get the information in the config file
pub fn read_json_config() -> JsonConfig {
    JSON_CONFIG
        .get_or_init(|| {
            debug!("Reading JSON config file");
            let exe_path = env::current_exe().unwrap().join("config.json");
            let pwd_path = env::current_dir().unwrap().join("config.json");
            let path = if exe_path.is_file() {
                exe_path
            } else if pwd_path.is_file() {
                pwd_path
            } else {
                error!(
                    "File config.json doesn't exist. Checked {} and {}",
                    exe_path.display(),
                    pwd_path.display()
                );
                exit(-1);
            };
            info!("Found {}", path.display());
            serde_json::from_str(fs::read_to_string(path).unwrap().as_str()).unwrap()
        })
        .clone()
}

/// Get the RSA private key
pub fn get_private_key() -> Rsa<Private> {
    PRIVATE_KEY
        .get_or_init(|| {
            debug!("Reading private RSA key file");
            let exe_path = env::current_exe().unwrap().join("private.pem");
            let pwd_path = env::current_dir().unwrap().join("private.pem");
            let path = if exe_path.is_file() {
                exe_path
            } else if pwd_path.is_file() {
                pwd_path
            } else {
                error!(
                    "File private.pem doesn't exist. Checked {} and {}",
                    exe_path.display(),
                    pwd_path.display()
                );
                exit(-1);
            };
            info!("Found {}", path.display());
            let content = fs::read(path).unwrap();
            Rsa::private_key_from_pem(content.as_slice())
                .expect("Could not decode Key File. Be sure that the file isn't encrypted")
        })
        .clone()
}

/// Get the API URL
pub fn get_api_url() -> Url {
    API_URL
        .get_or_init(|| {
            debug!("Parsing URL from config file");
            Url::parse(read_json_config().url.as_str()).unwrap()
        })
        .clone()
}
