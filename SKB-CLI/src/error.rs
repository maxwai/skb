use std::error;
use std::fmt::{Display, Formatter};

#[derive(Debug, Clone)]
pub struct AlreadySavedError;

impl Display for AlreadySavedError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "File is already saved on server")
    }
}

impl error::Error for AlreadySavedError {}

#[derive(Debug, Clone)]
pub struct FileNotFoundError;

impl Display for FileNotFoundError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "File was not found or is a directory")
    }
}

impl error::Error for FileNotFoundError {}

#[derive(Debug, Clone)]
pub struct NotSavedOnServerError;

impl Display for NotSavedOnServerError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "File is not saved on server")
    }
}

impl error::Error for NotSavedOnServerError {}

#[derive(Debug, Clone)]
pub struct ServerVersionIsNewerError;

impl Display for ServerVersionIsNewerError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "File version on server is newer")
    }
}

impl error::Error for ServerVersionIsNewerError {}

#[derive(Debug, Clone)]
pub struct LocalVersionIsNewerError;

impl Display for LocalVersionIsNewerError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "Local file version is newer")
    }
}

impl error::Error for LocalVersionIsNewerError {}

#[derive(Debug, Clone)]
pub struct ServerNotFoundError;

impl Display for ServerNotFoundError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "Server was not found")
    }
}

impl error::Error for ServerNotFoundError {}
