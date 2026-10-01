#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderPackDiagnostic {
    pub generation: u64,
    pub stage: &'static str,
    pub message: String,
}

#[derive(Default)]
pub struct ShaderPackDiagnostics {
    entries: Vec<ShaderPackDiagnostic>,
}

impl ShaderPackDiagnostics {
    pub fn record(&mut self, generation: u64, stage: &'static str, message: impl Into<String>) {
        self.entries.push(ShaderPackDiagnostic {
            generation,
            stage,
            message: message.into(),
        });
    }

    pub fn entries(&self) -> &[ShaderPackDiagnostic] {
        &self.entries
    }
}
