using PartialBuild.Core;

namespace PartialBuild.MSBuild;

public enum BuildLogStream
{
    StandardOutput,
    StandardError
}

public sealed class BuildLogEvent
{
    public required string BuildId { get; init; }

    public string? ProjectPath { get; init; }

    public string? ProjectName { get; init; }

    public BuildLogStream Stream { get; init; }

    public required string Message { get; init; }

    public DateTimeOffset Timestamp { get; init; } = DateTimeOffset.UtcNow;
}

public sealed class ProjectBuildStatusChangedEvent
{
    public required string BuildId { get; init; }

    public required string ProjectPath { get; init; }

    public required string ProjectName { get; init; }

    public ProjectExecutionState State { get; init; }

    public string? Message { get; init; }

    public DateTimeOffset Timestamp { get; init; } = DateTimeOffset.UtcNow;
}

public sealed class BuildRunResult
{
    public bool Succeeded { get; init; }

    public bool Cancelled { get; init; }

    public int ExitCode { get; init; }

    public string? ErrorMessage { get; init; }
}
