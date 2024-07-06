/// Test Module to test the models
#[cfg(test)]
use crate::models::{
    EmptyBody, FilePostRoot, InfoFile, InfoRoot, InfoServer, NewServerRoot, PostFileBody,
    ServerItem, ServerRoot,
};

/// Test the json deserializer for the `GET /info` API Path
#[test]
pub fn test_info_json() {
    let data = r#"
        {
            "total_usage_size": 123,
            "used_data": 123,
            "data_unsecured": 123,
            "data_secured": 123,
            "data_safely_secured": 123,
            "servers": [
                {
                    "hostname": "<hostname>",
                    "old_hostnames": [
                        "<hostname-old>",
                        "<hostname-old>2"
                    ],
                    "owner": "<owner>",
                    "block_size": 123,
                    "free_blocks": 123,
                    "used_blocks": 123,
                    "healthcheck_percent": 10,
                    "healthcheck_interval": 60,
                    "is_verified" : true,
                    "is_confirmed" : true,
                    "healthy": true
                }
            ],
            "files": [
                {
                    "id": "<uuid64>",
                    "path": "<path>",
                    "last_modified": 123456789
                },
                {
                    "id": "<uuid64>2",
                    "path": "<path>2",
                    "last_modified": 12345678
                }
            ]
        }"#;

    let info: InfoRoot =
        serde_json::from_str(data).unwrap_or_else(|err| panic!("Couldn't parse JSON: {:?}", err));
    assert_eq!(
        InfoRoot {
            total_usage_size: 123,
            used_data: 123,
            data_unsecured: 123,
            data_secured: 123,
            data_safely_secured: 123,
            servers: vec![InfoServer {
                hostname: "<hostname>".to_string(),
                old_hostnames: vec!["<hostname-old>".to_string(), "<hostname-old>2".to_string()],
                owner: "<owner>".to_string(),
                block_size: 123,
                free_blocks: 123,
                used_blocks: 123,
                healthcheck_percent: 10,
                healthcheck_interval: 60,
                is_verified: true,
                is_confirmed: true,
                healthy: true,
            }],
            files: vec![
                InfoFile {
                    id: "<uuid64>".to_string(),
                    path: "<path>".to_string(),
                    last_modified: 123456789,
                },
                InfoFile {
                    id: "<uuid64>2".to_string(),
                    path: "<path>2".to_string(),
                    last_modified: 12345678,
                },
            ],
        },
        info
    );
}

/// Test the json deserializer for the `POST /file` API Path
#[test]
fn test_file_post_json() {
    let data = r#"
        {
            "id": "<uuid64>"
        }"#;

    let info: FilePostRoot =
        serde_json::from_str(data).unwrap_or_else(|err| panic!("Couldn't parse JSON: {:?}", err));
    assert_eq!(
        FilePostRoot {
            id: "<uuid64>".to_string()
        },
        info
    );
}

/// Test the json deserializer for the `GET /server` API Path
#[test]
fn test_server_json() {
    let data = r#"
        {
            "servers": [
                {
                    "hostname": "<hostname>",
                    "owner": "<owner>",
                    "block_size": 123,
                    "free_blocks": 123,
                    "healthcheck_percent": 10,
                    "healthcheck_interval": 60,
                    "hash_methods": [
                        "<method>",
                        "<method2>"
                    ]
                }
            ]
        }"#;

    let info: ServerRoot =
        serde_json::from_str(data).unwrap_or_else(|err| panic!("Couldn't parse JSON: {:?}", err));
    assert_eq!(
        ServerRoot {
            servers: vec![ServerItem {
                hostname: "<hostname>".to_string(),
                owner: "<owner>".to_string(),
                block_size: 123,
                free_blocks: 123,
                healthcheck_percent: 10,
                healthcheck_interval: 60,
                hash_methods: vec!["<method>".to_string(), "<method2>".to_string()]
            }]
        },
        info
    );
}

/// Test the json serializer for the empty request body
#[test]
fn test_empty_body_json() {
    let data = EmptyBody {
        nonce: "this-is-a-nonce".to_string(),
    };

    let info = serde_json::to_string(&data)
        .unwrap_or_else(|err| panic!("Couldn't convert to JSON: {:?}", err));
    assert_eq!(r#"{"nonce":"this-is-a-nonce"}"#, info);
}

/// Test the json serializer for the `POST /file` API Path request body
#[test]
fn test_file_post_response_json() {
    let data = PostFileBody {
        path: "/path".to_string(),
        nonce: "this-is-a-nonce".to_string(),
    };

    let info = serde_json::to_string(&data)
        .unwrap_or_else(|err| panic!("Couldn't convert to JSON: {:?}", err));
    assert_eq!(r#"{"path":"/path","nonce":"this-is-a-nonce"}"#, info);
}

/// Test the json deserializer for the `POST /server` and `PUT /server` API Path
#[test]
fn test_new_server_json() {
    let data = r#"
        {
            "backup_code": "<backup-code>"
        }"#;

    let info: NewServerRoot =
        serde_json::from_str(data).unwrap_or_else(|err| panic!("Couldn't parse JSON: {:?}", err));
    assert_eq!(
        NewServerRoot {
            backup_code: "<backup-code>".to_string()
        },
        info
    );
}
