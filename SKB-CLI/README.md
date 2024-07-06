# Generating RSA Key pair

```bash
openssl genrsa -out private.pem 2048
openssl rsa -in private.pem -outform PEM -pubout -out public.pem
```

# Config file

The program needs a `config.json`. Here is an example what it looks like:

```json
{
  "url": "<url-to-skb-api>"
}
```

# Run program

```bash
cargo run
```

# Build program

```bash
cargo build
```

Release build:

```bash
cargo build --release
```

# Test

```bash
cargo test
```

# Documentation

```bash
cargo doc --no-deps
```

# Roadmap

- Healthcheck interval in human readable format
- Add possibility to specify folder to files and then target every file in the folder and subfolders
- When listing files, if all files in a directory are synced, group them and only show directory
- For a specified directory, list files that are not synced on the server
