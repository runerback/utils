using PartialBuild.Core;
using PartialBuild.MSBuild;

namespace PartialBuild.Server;

public sealed class ProjectBuildStatusSnapshot
{
    public required string ProjectPath { get; init; }

    public required string ProjectName { get; init; }

    public ProjectExecutionState State { get; set; }

    public string? Message { get; set; }

    public DateTimeOffset? StartedAt { get; set; }

    public DateTimeOffset? CompletedAt { get; set; }
}

public sealed class BuildSessionSnapshot
{
    public required string Id { get; init; }

    public required bool IsBootstrap { get; init; }

    public required BuildExecutionState State { get; init; }

    public required BuildContextOptions Context { get; init; }

    public BuildPlan? Plan { get; init; }

    public required List<ProjectBuildStatusSnapshot> Projects { get; init; }

    public required List<BuildLogEvent> RecentLogs { get; init; }

    public string? LogFilePath { get; init; }

    public string? ErrorMessage { get; init; }

    public DateTimeOffset CreatedAt { get; init; }

    public DateTimeOffset? StartedAt { get; init; }

    public DateTimeOffset? CompletedAt { get; init; }
}
