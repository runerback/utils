namespace PartialBuild.Core;

public enum ProjectGraphLoadStage
{
    Started,
    Loading,
    Loaded,
    Completed,
    Failed
}

public sealed class ProjectGraphLoadProgress
{
    public required string ProgressId { get; init; }

    public required ProjectGraphLoadStage Stage { get; init; }

    public string? ProjectPath { get; init; }

    public string? ProjectName { get; init; }

    public int LoadedCount { get; init; }

    public int DiscoveredCount { get; init; }

    public int PendingCount { get; init; }

    public required string Message { get; init; }

    public DateTimeOffset Timestamp { get; init; } = DateTimeOffset.UtcNow;
}
