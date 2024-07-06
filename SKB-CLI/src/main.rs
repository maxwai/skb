//! # SKB CLI
//!
//! CLI programm to communicate with the SKB server

#![cfg_attr(feature = "fail-on-warnings", deny(warnings))]

use std::cmp::Ordering;
use std::fs::File;
use std::path::PathBuf;
use std::time::SystemTime;

use chrono::{DateTime, Utc};
use human_bytes::human_bytes;
use log::{debug, error, info, warn};

use crate::env::{read_json_config, Commands, FileCommands, ServerCommands};
use crate::error::{
    AlreadySavedError, FileNotFoundError, LocalVersionIsNewerError, NotSavedOnServerError,
    ServerNotFoundError, ServerVersionIsNewerError,
};
use crate::models::NewServerRoot;

mod env;
mod error;
mod models;
mod rest_api;

/// Change the alias to use `Box<dyn error::Error>`.
#[doc(hidden)]
type Result<T> = std::result::Result<T, Box<dyn std::error::Error>>;

#[tokio::main]
async fn main() -> Result<()> {
    env_logger::init_from_env(env_logger::Env::default().filter_or(
        env_logger::DEFAULT_FILTER_ENV,
        match env::get_cli_args().verbose {
            0 => "warn",
            1 => "info",
            2 => "debug",
            _ => "trace",
        },
    ));
    debug!("Reading json config file");
    read_json_config();
    debug!("Getting CLI Command");
    match env::get_cli_args().command {
        Commands::Status => status_command().await,
        Commands::Server { command } => server_command(command).await,
        Commands::File { command } => file_command(command).await,
    }
}

/// The status command
async fn status_command() -> Result<()> {
    debug!(r#"Got "status" command"#);
    match rest_api::get_info().await {
        Ok(info) => {
            debug!("Got information from server");
            println!(
                "Info for server {}://{}{}",
                env::get_api_url().scheme(),
                env::get_api_url()
                    .host_str()
                    .unwrap_or(env::get_api_url().as_str()),
                env::get_api_url()
                    .port()
                    .map(|port| format!(":{}", port))
                    .unwrap_or_default()
            );
            println!();
            println!(
                "Size of backup System:  {}",
                human_bytes(info.total_usage_size as f64)
            );
            println!(
                "Used space:             {} ({}%)",
                human_bytes(info.used_data as f64),
                info.used_data * 100 / info.total_usage_size
            );
            println!(
                "Free space:             {} ({}%)",
                human_bytes((info.total_usage_size - info.used_data) as f64),
                (info.total_usage_size - info.used_data) * 100 / info.total_usage_size
            );
            println!(
                "Data unsecured:         {}",
                human_bytes(info.data_unsecured as f64)
            );
            println!(
                "Data only once secured: {}",
                human_bytes(info.data_secured as f64)
            );
            println!(
                "Data safely secured:    {}",
                human_bytes(info.data_safely_secured as f64)
            );
            println!();
            println!("List of connected servers:");
            for server in info.servers {
                if server.is_verified && server.is_confirmed {
                    println!(
                        "{} [{}]: free {}, used {}, {}",
                        server.hostname,
                        server.owner,
                        human_bytes((server.block_size * server.free_blocks) as f64),
                        human_bytes((server.block_size * server.used_blocks) as f64),
                        if server.healthy {
                            "healthy"
                        } else {
                            "unhealthy"
                        }
                    );
                } else if server.is_verified && !server.is_confirmed {
                    println!(
                        "{} [{}]: waiting for remote confirmation",
                        server.hostname, server.owner
                    );
                } else if !server.is_verified && server.is_confirmed {
                    println!(
                        "{} [{}]: waiting for confirmation",
                        server.hostname, server.owner
                    );
                } else {
                    warn!(
                        "Got illegal state for server {}, both is_verified and is_confirmed are false",
                        server.hostname
                    );
                }
            }
            println!();
            println!("Saved files:");
            for file in info.files {
                println!("{}", file.path);
            }
            Ok(())
        }
        Err(err) => {
            error!("Problem getting the server information");
            Err(err)
        }
    }
}

/// The main server command
async fn server_command(command: ServerCommands) -> Result<()> {
    debug!(r#"Got "server" command"#);
    match command {
        ServerCommands::List => server_list_command().await,
        ServerCommands::Discover { depth } => server_discover_command(depth).await,
        ServerCommands::Verify { hostname } => server_verify_command(hostname).await,
        ServerCommands::New { hostname } => server_new_command(hostname).await,
        ServerCommands::Delete { hostname } => server_delete_command(hostname).await,
    }
}

/// The server list subcommand
async fn server_list_command() -> Result<()> {
    debug!(r#"Got "list" subcommand of the "server" command"#);
    match rest_api::get_info().await {
        Ok(info) => {
            debug!("Got information from server");
            for server in info.servers {
                if !server.is_verified && !server.is_confirmed {
                    warn!(
                        "Got illegal state for server {}, both is_verified and is_confirmed are false",
                        server.hostname
                    );
                    break;
                }
                println!("{}", server.hostname);
                println!("\tOwner:                {}", server.owner);
                println!(
                    "\tKnown, old hostnames: {}",
                    server.old_hostnames.first().unwrap_or(&"None".to_string())
                );
                for hostname in server.old_hostnames.iter().skip(1) {
                    println!("\t                      {}", hostname);
                }
                println!(
                    "\tBlock size:           {}",
                    human_bytes(server.block_size as f64)
                );
                println!(
                    "\tFree Blocks:          {} ({})",
                    server.free_blocks,
                    human_bytes((server.block_size * server.free_blocks) as f64)
                );
                println!(
                    "\tUsed Blocks:          {} ({})",
                    server.used_blocks,
                    human_bytes((server.block_size * server.used_blocks) as f64)
                );
                // TODO: format time to human readable format
                println!(
                    "\tHealthcheck:          {}% every {}min",
                    server.healthcheck_percent, server.healthcheck_interval
                );
                println!(
                    "\tVerified:             {}",
                    if server.is_verified && server.is_confirmed {
                        "Yes"
                    } else if server.is_verified && !server.is_confirmed {
                        "Waiting for remote server to verify"
                    } else if !server.is_verified && server.is_confirmed {
                        "Waiting for confirmation"
                    } else {
                        panic!(
                            "This statement should never be executed because of a prior statement"
                        );
                    }
                );
                println!("\tHealthy:              {}", server.healthy);
            }
            Ok(())
        }
        Err(err) => {
            error!("Problem getting the server information");
            Err(err)
        }
    }
}

/// The server discover subcommand
async fn server_discover_command(depth: Option<u8>) -> Result<()> {
    debug!(r#"Got "discover" subcommand of the "server" command"#);
    match rest_api::get_servers(depth).await {
        Ok(servers) => {
            debug!("Got server list from server");
            for server in servers.servers {
                println!("{}", server.hostname);
                println!("\tOwner:        {}", server.owner);
                println!("\tBlock size:   {}", human_bytes(server.block_size as f64));
                println!(
                    "\tFree Blocks:  {} ({})",
                    server.free_blocks,
                    human_bytes((server.block_size * server.free_blocks) as f64)
                );
                // TODO: format time to human readable format
                println!(
                    "\tHealthcheck:  {}% every {}min",
                    server.healthcheck_percent, server.healthcheck_interval
                );
                println!(
                    "\tHash Methods: {}",
                    server.hash_methods.first().unwrap_or(&"None".to_string())
                );
                for hash_method in server.hash_methods.iter().skip(1) {
                    println!("\t              {}", hash_method);
                }
            }
            Ok(())
        }
        Err(err) => {
            error!("Problem discovering servers");
            Err(err)
        }
    }
}

/// The server verify subcommand
async fn server_verify_command(hostname: String) -> Result<()> {
    debug!(r#"Got "verify" subcommand of the "server" command"#);
    match rest_api::get_info().await {
        Ok(info) => {
            debug!("Got information from server");
            for server in info.servers {
                if server.hostname == hostname {
                    if server.is_verified {
                        println!("Server is already confirmed");
                    } else {
                        return match rest_api::accept_server(&hostname).await {
                            Ok(NewServerRoot { backup_code }) => {
                                println!(
                                    "Confirmed Server {}. Here is the Backup Code, write it down somewhere:",
                                    hostname
                                );
                                println!("{}", backup_code);
                                Ok(())
                            }
                            Err(err) => {
                                error!("Couldn't confirm server");
                                Err(err)
                            }
                        };
                    }
                    return Ok(());
                }
            }
            warn!("Server hostname is not known");
            Err(ServerNotFoundError.into())
        }
        Err(err) => {
            error!("Problem getting the server information");
            Err(err)
        }
    }
}

/// The server new subcommand
async fn server_new_command(hostname: String) -> Result<()> {
    debug!(r#"Got "new" subcommand of the "server" command"#);
    match rest_api::get_info().await {
        Ok(info) => {
            debug!("Got information from server");
            for server in info.servers {
                if server.hostname == hostname {
                    println!("Server is already added");
                    return Ok(());
                }
            }
            match rest_api::add_new_server(&hostname).await {
                Ok(NewServerRoot { backup_code }) => {
                    println!(
                        "Added new Server {}. Here is the Backup Code, write it down somewhere:",
                        hostname
                    );
                    println!("{}", backup_code);
                    Ok(())
                }
                Err(err) => {
                    error!("Couldn't add new server");
                    Err(err)
                }
            }
        }
        Err(err) => {
            error!("Problem getting the server information");
            Err(err)
        }
    }
}

/// The server delete subcommand
async fn server_delete_command(hostname: String) -> Result<()> {
    debug!(r#"Got "delete" subcommand of the "server" command"#);
    match rest_api::get_info().await {
        Ok(info) => {
            debug!("Got information from server");
            for server in info.servers {
                if server.hostname == hostname {
                    return match rest_api::delete_server(&hostname).await {
                        Ok(()) => {
                            println!("Deleted Server {}", hostname);
                            Ok(())
                        }
                        Err(err) => {
                            error!("Couldn't delete server");
                            Err(err)
                        }
                    };
                }
            }
            warn!("Server hostname is not known");
            Err(ServerNotFoundError.into())
        }
        Err(err) => {
            error!("Problem getting the server information");
            Err(err)
        }
    }
}

/// The main file command
async fn file_command(command: FileCommands) -> Result<()> {
    debug!(r#"Got "file" command"#);
    match command {
        FileCommands::List => file_list_command().await,
        FileCommands::Add { file } => file_add_command(file).await,
        FileCommands::Update { file, force } => file_update_command(file, force).await,
        FileCommands::Download { file, force } => file_download_command(file, force).await,
        FileCommands::Delete { file } => file_delete_command(file).await,
        FileCommands::Sync => file_sync_command().await,
    }
}

/// The file list subcommand
async fn file_list_command() -> Result<()> {
    debug!(r#"Got "list" subcommand of the "file" command"#);
    match rest_api::get_info().await {
        Ok(info) => {
            debug!("Got information from server");
            for file in info.files {
                let path = PathBuf::from(file.path.as_str());
                println!(
                    "{}: {}",
                    if path.is_file() {
                        let last_modified = DateTime::<Utc>::from(
                            File::open(path)?
                                .metadata()?
                                .modified()
                                .unwrap_or(SystemTime::now()),
                        );
                        match last_modified.timestamp().cmp(&file.last_modified) {
                            Ordering::Less => "OUTDATED",
                            Ordering::Equal => "UP TO DATE",
                            Ordering::Greater => "NOT SYNCED",
                        }
                    } else {
                        "NOT FOUND LOCALLY"
                    },
                    file.path
                );
            }
            Ok(())
        }
        Err(err) => {
            error!("Problem getting the server information");
            Err(err)
        }
    }
}

/// The file add subcommand
async fn file_add_command(file: PathBuf) -> Result<()> {
    debug!(r#"Got "add" subcommand of the "file" command"#);
    if !file.is_file() {
        warn!("File {} was not found or is a directory", file.display());
        return Err(FileNotFoundError.into());
    }
    match rest_api::get_info().await {
        Ok(info) => {
            debug!("Got information from server");
            for saved_file in info.files {
                let saved_file_path = PathBuf::from(saved_file.path.as_str());
                if file == saved_file_path {
                    warn!("File {} is already synced on server", file.display());
                    return Err(AlreadySavedError.into());
                }
            }
            match rest_api::create_new_file(&file).await {
                Ok(info) => match rest_api::upload_new_file(&info.id, &file).await {
                    Ok(()) => {
                        println!("File was uploaded to the server");
                        Ok(())
                    }
                    Err(err) => {
                        error!("Could not upload file on server after creating it");
                        Err(err)
                    }
                },
                Err(err) => {
                    error!("Could not create new file on server");
                    Err(err)
                }
            }
        }
        Err(err) => {
            error!("Problem getting the server information");
            Err(err)
        }
    }
}

/// The file update subcommand
async fn file_update_command(file: PathBuf, force: bool) -> Result<()> {
    debug!(r#"Got "update" subcommand of the "file" command"#);
    if !file.is_file() {
        warn!("File {} was not found or is a directory", file.display());
        return Err(FileNotFoundError.into());
    }
    match rest_api::get_info().await {
        Ok(info) => {
            debug!("Got information from server");
            for saved_file in info.files {
                let saved_file_path = PathBuf::from(saved_file.path.as_str());
                if file == saved_file_path {
                    let last_modified = DateTime::<Utc>::from(
                        File::open(&file)?
                            .metadata()?
                            .modified()
                            .unwrap_or(SystemTime::now()),
                    );
                    return match last_modified.timestamp().cmp(&saved_file.last_modified) {
                        Ordering::Less if !force => {
                            println!("File is newer on server. Force update with -f flag");
                            Err(ServerVersionIsNewerError.into())
                        }
                        Ordering::Equal => {
                            println!("File is already up to date");
                            Ok(())
                        }
                        Ordering::Greater | Ordering::Less => {
                            match rest_api::update_file(&saved_file.id, &file).await {
                                Ok(()) => {
                                    println!("Server file updated with local file version");
                                    Ok(())
                                }
                                Err(err) => {
                                    error!("Could not update file on server");
                                    Err(err)
                                }
                            }
                        }
                    };
                }
            }
            warn!("File {} is not synced on server", file.display());
            Err(NotSavedOnServerError.into())
        }
        Err(err) => {
            error!("Problem getting the server information");
            Err(err)
        }
    }
}

/// The file download subcommand
async fn file_download_command(file: PathBuf, force: bool) -> Result<()> {
    debug!(r#"Got "download" subcommand of the "file" command"#);
    match rest_api::get_info().await {
        Ok(info) => {
            debug!("Got information from server");
            for saved_file in info.files {
                let saved_file_path = PathBuf::from(saved_file.path.as_str());
                if file == saved_file_path {
                    if file.is_file() {
                        let last_modified = DateTime::<Utc>::from(
                            File::open(&file)?
                                .metadata()?
                                .modified()
                                .unwrap_or(SystemTime::now()),
                        );
                        match last_modified.timestamp().cmp(&saved_file.last_modified) {
                            Ordering::Greater if !force => {
                                println!("Local file is newer. Force download with -f flag");
                                return Err(LocalVersionIsNewerError.into());
                            }
                            Ordering::Equal => {
                                println!("File is already up to date");
                                return Ok(());
                            }
                            Ordering::Less | Ordering::Greater => {}
                        };
                    }
                    return match rest_api::get_file(&saved_file.id, &file).await {
                        Ok(()) => {
                            println!("Local file updated with server version");
                            Ok(())
                        }
                        Err(err) => {
                            error!("Could not download file from server");
                            Err(err)
                        }
                    };
                }
            }
            warn!("File {} is not synced on server", file.display());
            Err(NotSavedOnServerError.into())
        }
        Err(err) => {
            error!("Problem getting the server information");
            Err(err)
        }
    }
}

/// The file download subcommand
async fn file_delete_command(file: PathBuf) -> Result<()> {
    debug!(r#"Got "delete" subcommand of the "file" command"#);
    match rest_api::get_info().await {
        Ok(info) => {
            debug!("Got information from server");
            for saved_file in info.files {
                if file == PathBuf::from(saved_file.path.as_str()) {
                    return match rest_api::delete_file(&saved_file.id).await {
                        Ok(()) => {
                            println!("Deleted file on server. Local file is not deleted");
                            Ok(())
                        }
                        Err(err) => {
                            error!("Could not delete file from server");
                            Err(err)
                        }
                    };
                }
            }
            warn!("File {} is not synced on server", file.display());
            Err(NotSavedOnServerError.into())
        }
        Err(err) => {
            error!("Problem getting the server information");
            Err(err)
        }
    }
}

/// The file update subcommand
async fn file_sync_command() -> Result<()> {
    debug!(r#"Got "sync" subcommand of the "file" command"#);
    match rest_api::get_info().await {
        Ok(info) => {
            debug!("Got information from server");
            for saved_file in info.files {
                let file_path = PathBuf::from(saved_file.path.as_str());
                if file_path.is_file() {
                    let last_modified = DateTime::<Utc>::from(
                        File::open(&file_path)?
                            .metadata()?
                            .modified()
                            .unwrap_or(SystemTime::now()),
                    );
                    match last_modified.timestamp().cmp(&saved_file.last_modified) {
                        Ordering::Greater => {
                            match rest_api::update_file(&saved_file.id, &file_path).await {
                                Ok(()) => {
                                    println!("UPLOADED: {}", file_path.display());
                                    continue;
                                }
                                Err(err) => {
                                    error!(
                                        "Could not update file on server: {}",
                                        file_path.display()
                                    );
                                    return Err(err);
                                }
                            };
                        }
                        Ordering::Equal => {
                            info!("{} already up to date", file_path.display());
                            continue;
                        }
                        Ordering::Less => {}
                    };
                }
                match rest_api::get_file(&saved_file.id, &file_path).await {
                    Ok(()) => {
                        println!("DOWNLOADED: {}", file_path.display());
                    }
                    Err(err) => {
                        error!("Could not download file from server");
                        return Err(err);
                    }
                };
            }
            println!("Updated every file");
            Ok(())
        }
        Err(err) => {
            error!("Problem getting the server information");
            Err(err)
        }
    }
}
