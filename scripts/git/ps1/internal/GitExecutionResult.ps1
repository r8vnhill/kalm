# Define the GitExecutionResult class only if it has not already been defined.
# This prevents duplicate class definition errors in interactive or multi-source
# environments.
if (-not ('GitExecutionResult' -as [type])) {

    # GitExecutionResult encapsulates the result of executing a Git command.
    class GitExecutionResult {

        # The full Git command that was executed (as a string, for reference or logging).
        [string] $Command

        # The exit code returned by the Git command (0 typically indicates success).
        [int] $ExitCode

        # The full stdout/stderr output of the command, joined into a single string.
        [string] $Output

        # Constructor for GitExecutionResult.
        # Initializes the Command, ExitCode, and Output fields.
        GitExecutionResult([string] $Command, [int] $ExitCode, [string] $Output) {
            $this.Command   = $Command
            $this.ExitCode  = $ExitCode
            $this.Output    = $Output
        }

        # Returns a formatted string representation of the result.
        # Useful for logging or diagnostics.
        [string] ToString() {
            return (@(
                "Git Command: $($this.Command)",
                "Exit Code: $($this.ExitCode)",
                "Output: $($this.Output -join "`n")"
            ) -join "`n").Trim()
        }

        # Indicates whether the Git command executed successfully (exit code 0).
        [bool] IsSuccess() {
            return $this.ExitCode -eq 0
        }

        # Returns the command output as an array of individual lines.
        [string[]] GetOutputLines() {
            return $this.Output -split "`r?`n"
        }
    }
}
