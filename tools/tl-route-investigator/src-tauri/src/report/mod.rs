mod build;
mod export;

pub use build::{build_report, DiagnosticReport};
pub use export::{export_report_to_path, render, ReportFormat};
