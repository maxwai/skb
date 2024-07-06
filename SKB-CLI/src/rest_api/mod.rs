//! # REST API Interface
//!
//! Interface to the REST API

use std::error;
use std::fs::{self, File};
use std::io::{BufReader, Read, Write};
use std::path::Path;
use std::time::SystemTime;

use base64::engine::general_purpose;
use base64::Engine;
use chrono::{DateTime, Local, Utc};
use log::{debug, info};
use openssl::hash::MessageDigest;
use openssl::pkey::PKey;
use openssl::sign::Signer;
use rand::RngCore;
use reqwest::{header, Client, Url, ClientBuilder};

use crate::env;
use crate::models::{EmptyBody, FilePostRoot, InfoRoot, NewServerRoot, PostFileBody, ServerRoot};

/// Change the alias to use `Box<dyn error::Error>`.
#[doc(hidden)]
type Result<T> = std::result::Result<T, Box<dyn error::Error>>;

/// Creates the correct client
fn get_client() -> Client {
    if env::get_cli_args().allow_unsafe {
        ClientBuilder::new().danger_accept_invalid_certs(true).build().unwrap()
    } else {
        Client::new()
    }
}

/// Get a random nonce, 128 bytes in length, encoded in Base64
fn get_nonce() -> String {
    debug!("Generating random 128 bytes long nonce");
    let mut nonce = [0u8; 128];
    rand::thread_rng().fill_bytes(&mut nonce);
    general_purpose::STANDARD.encode(nonce)
}

/// Calculate the signature for the given body and return it, encoded in Base64
pub fn sign_bytes(body: &[u8]) -> String {
    debug!("Singing body with private key with SHA256withRSA");
    let keypair = PKey::from_rsa(env::get_private_key()).unwrap();
    let mut signer = Signer::new(MessageDigest::sha256(), &keypair).unwrap();
    signer.update(body).unwrap();
    general_purpose::STANDARD.encode(signer.sign_to_vec().unwrap())
}

/// Gets the [InfoRoot] struct from API
pub async fn get_info() -> Result<InfoRoot> {
    let client = get_client();
    let body = serde_json::to_string(&EmptyBody { nonce: get_nonce() })?;
    let signature = sign_bytes(body.as_bytes());
    info!(r#"Calling "GET /info""#);
    let response = client
        .get(env::get_api_url().join("info/")?)
        .body(body)
        .header("SIGNATURE", signature)
        .send()
        .await?
        .error_for_status()?;
    Ok(response.json().await?)
}

/// Create a new file entry on the server
pub async fn create_new_file(file_path: &Path) -> Result<FilePostRoot> {
    let client = get_client();
    let body = serde_json::to_string(&PostFileBody {
        path: file_path.display().to_string(),
        nonce: get_nonce(),
    })?;
    let signature = sign_bytes(body.as_bytes());
    info!(r#"Calling "POST /file" for file {}"#, file_path.display());
    let response = client
        .post(env::get_api_url().join("file/")?)
        .body(body)
        .header("SIGNATURE", signature)
        .send()
        .await?
        .error_for_status()?;
    Ok(response.json().await?)
}

/// Upload a new file after creating it
pub async fn upload_new_file(id: &String, file_path: &Path) -> Result<()> {
    let client = get_client();

    let file = File::open(file_path)?;
    let mut reader = BufReader::new(&file);
    let mut buffer: Vec<u8> = Vec::new();
    reader.read_to_end(&mut buffer)?;
    let signature = sign_bytes(buffer.as_slice());

    info!(
        r#"Calling "POST /file/{}" for file {}"#,
        id,
        file_path.display()
    );
    client
        .post(env::get_api_url().join(format!("file/{}/", id).as_str())?)
        .body(buffer)
        .header("SIGNATURE", signature)
        .header(
            header::LAST_MODIFIED,
            format!(
                "{}",
                DateTime::<Utc>::from(file.metadata()?.modified().unwrap_or(SystemTime::now()))
                    .format("%a, %d %b %Y %H:%M:%S GMT")
            ),
        )
        .header(header::CONTENT_TYPE, "application/octet-stream")
        .send()
        .await?
        .error_for_status()?;
    Ok(())
}

/// Update a file on the server
pub async fn update_file(id: &String, file_path: &Path) -> Result<()> {
    let client = get_client();

    let file = File::open(file_path)?;
    let mut reader = BufReader::new(&file);
    let mut buffer: Vec<u8> = Vec::new();
    reader.read_to_end(&mut buffer)?;
    let signature = sign_bytes(buffer.as_slice());

    info!(
        r#"Calling "PUT /file/{}" for file {}"#,
        id,
        file_path.display()
    );
    client
        .put(env::get_api_url().join(format!("file/{}/", id).as_str())?)
        .body(buffer)
        .header("SIGNATURE", signature)
        .header(
            header::LAST_MODIFIED,
            format!(
                "{}",
                DateTime::<Utc>::from(file.metadata()?.modified().unwrap_or(SystemTime::now()))
                    .format("%a, %d %b %Y %H:%M:%S GMT")
            ),
        )
        .header(header::CONTENT_TYPE, "application/octet-stream")
        .send()
        .await?
        .error_for_status()?;
    Ok(())
}

/// Get the version of the file that is on the server. The local file will be overwritten if it
/// exists.
pub async fn get_file(id: &String, file_path: &Path) -> Result<()> {
    let client = get_client();

    let body = serde_json::to_string(&EmptyBody { nonce: get_nonce() })?;
    let signature = sign_bytes(body.as_bytes());

    info!(
        r#"Calling "GET /file/{}" for file {}"#,
        id,
        file_path.display()
    );
    let response = client
        .get(env::get_api_url().join(format!("file/{}/", id).as_str())?)
        .body(body)
        .header("SIGNATURE", signature)
        .send()
        .await?
        .error_for_status()?;

    let last_modified = response
        .headers()
        .get(header::LAST_MODIFIED)
        .and_then(|value| {
            DateTime::parse_from_str(
                value.to_str().unwrap_or_default(),
                "%a, %d %b %Y %H:%M:%S %Z",
            )
            .ok()
            .map(|timestamp| SystemTime::from(timestamp.with_timezone(&Local)))
        })
        .unwrap_or(SystemTime::now());
    let file_data = response.bytes().await?;

    debug!("Opening {} for replacement", file_path.display());
    let mut file = fs::OpenOptions::new()
        .create(true)
        .truncate(true)
        .write(true)
        .open(file_path)?;

    info!(
        "Replacing {} with data gotten from server",
        file_path.display()
    );
    file.write_all(file_data.as_ref())?;
    file.set_modified(last_modified)?;

    Ok(())
}

/// Delete file on the server. This will not delete the file on the local file system.
pub async fn delete_file(id: &String) -> Result<()> {
    let client = get_client();
    let body = serde_json::to_string(&EmptyBody { nonce: get_nonce() })?;
    let signature = sign_bytes(body.as_bytes());
    info!(r#"Calling "DELETE /file/{}""#, id);
    let _response = client
        .delete(env::get_api_url().join(format!("file/{}/", id).as_str())?)
        .body(body)
        .header("SIGNATURE", signature)
        .send()
        .await?
        .error_for_status()?;
    Ok(())
}

/// Gets the [ServerRoot] struct from the API
pub async fn get_servers(depth: Option<u8>) -> Result<ServerRoot> {
    let client = get_client();
    let body = serde_json::to_string(&EmptyBody { nonce: get_nonce() })?;
    let signature = sign_bytes(body.as_bytes());
    let mut api_url = env::get_api_url().join("server/")?;
    if let Some(depth) = depth {
        api_url = Url::parse_with_params(api_url.as_str(), [("depth", depth.to_string())])?;
    }
    info!(
        r#"Calling "GET /server"{}"#,
        if let Some(depth) = depth {
            format!(" with a depth of {}", depth)
        } else {
            "".to_string()
        }
    );
    let response = client
        .get(api_url)
        .body(body)
        .header("SIGNATURE", signature)
        .send()
        .await?
        .error_for_status()?;
    Ok(response.json().await?)
}

/// Add a new remote server
pub async fn add_new_server(new_server: &String) -> Result<NewServerRoot> {
    let client = get_client();
    let body = serde_json::to_string(&EmptyBody { nonce: get_nonce() })?;
    let signature = sign_bytes(body.as_bytes());
    let api_url = env::get_api_url().join("server/")?;
    let api_url = Url::parse_with_params(api_url.as_str(), [("hostname", &new_server)])?;
    info!(r#"Calling "POST /server/" for server {}"#, new_server);
    let response = client
        .post(api_url)
        .body(body)
        .header("SIGNATURE", signature)
        .send()
        .await?
        .error_for_status()?;
    Ok(response.json().await?)
}

/// Accept a remote server that is trying to make a connection with this server
pub async fn accept_server(new_server: &String) -> Result<NewServerRoot> {
    let client = get_client();
    let body = serde_json::to_string(&EmptyBody { nonce: get_nonce() })?;
    let signature = sign_bytes(body.as_bytes());
    let api_url = env::get_api_url().join("server/")?;
    let api_url = Url::parse_with_params(api_url.as_str(), [("hostname", &new_server)])?;
    info!(r#"Calling "PUT /server/" for server {}"#, new_server);
    let response = client
        .put(api_url)
        .body(body)
        .header("SIGNATURE", signature)
        .send()
        .await?
        .error_for_status()?;
    Ok(response.json().await?)
}

/// Delete a known server, deleting all saved blocks on that server if there are any
pub async fn delete_server(new_server: &String) -> Result<()> {
    let client = get_client();
    let body = serde_json::to_string(&EmptyBody { nonce: get_nonce() })?;
    let signature = sign_bytes(body.as_bytes());
    let api_url = env::get_api_url().join("server/")?;
    let api_url = Url::parse_with_params(api_url.as_str(), [("hostname", &new_server)])?;
    info!(r#"Calling "DELETE /server/" for server {}"#, new_server);
    client
        .delete(api_url)
        .body(body)
        .header("SIGNATURE", signature)
        .send()
        .await?
        .error_for_status()?;
    Ok(())
}
