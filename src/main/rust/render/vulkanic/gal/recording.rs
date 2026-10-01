//! Command recording scopes and command lists.

use super::*;

impl VulkanicGal {
    /// Opens a nestable command-recording lifetime. Destruction remains
    /// dependency checked, but the handle slot is not released until the
    /// outermost scope closes after submission or command abandonment.
    pub fn begin_command_recording(&mut self) -> GalResult<()> {
        self.command_recording_depth =
            self.command_recording_depth.checked_add(1).ok_or_else(|| {
                GalError::invalid_argument("GAL command-recording scope depth exhausted")
            })?;
        Ok(())
    }

    /// How many destroys are queued until the outermost command-recording
    /// scope finishes.
    pub fn command_recording_deferred_destroy_count(&self) -> usize {
        self.command_recording_destroys.len()
    }

    /// Closes one command-recording lifetime and applies deferred destroys in
    /// their original dependency order at the outermost boundary.
    pub fn finish_command_recording(&mut self) -> GalResult<()> {
        if self.command_recording_depth == 0 {
            return Err(GalError::invalid_argument(
                "GAL command-recording scope is not active",
            ));
        }
        self.command_recording_depth -= 1;
        if self.command_recording_depth != 0 {
            return Ok(());
        }
        let mut destroys = std::mem::take(&mut self.command_recording_destroys);
        self.command_recording_destroy_set.clear();
        let mut first_error = None;
        for handle in destroys.iter().copied() {
            if let Err(error) = self.destroy_now(handle) {
                if first_error.is_none() {
                    first_error = Some(error);
                }
            }
        }
        // Keep the steady-state allocation owned by the GAL. These handles
        // are ephemeral frame resources, so discarding the Vec capacity here
        // forced the same backing allocation to be rebuilt every frame.
        destroys.clear();
        self.command_recording_destroys = destroys;
        match first_error {
            Some(error) => Err(error),
            None => Ok(()),
        }
    }

    /// Validates a list of command ops and wraps it as a `CommandList` for
    /// submission. The op count must fit the backend limit.
    pub fn create_command_list(&mut self, desc: CommandListDesc) -> GalResult<CommandList> {
        let capabilities = self.capabilities();
        if desc.operations.len() > capabilities.limits.max_commands_per_list as usize {
            return self.unsupported(format!(
                "command list '{}' operation count {} exceeds backend '{}' limit {}",
                desc.label,
                desc.operations.len(),
                capabilities.name,
                capabilities.limits.max_commands_per_list
            ));
        }
        self.validate_command_ops(&desc.label, &desc.operations)?;
        Ok(desc.into())
    }
}
