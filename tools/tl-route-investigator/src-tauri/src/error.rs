use std::fmt;

#[derive(Debug)]
pub enum AppError {
    Message(String),
}

impl fmt::Display for AppError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            AppError::Message(m) => write!(f, "{m}"),
        }
    }
}

impl std::error::Error for AppError {}

impl From<String> for AppError {
    fn from(value: String) -> Self {
        AppError::Message(value)
    }
}

impl From<&str> for AppError {
    fn from(value: &str) -> Self {
        AppError::Message(value.to_string())
    }
}

pub type AppResult<T> = Result<T, AppError>;
