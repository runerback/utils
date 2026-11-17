using System.Collections.Concurrent;
using PartialBuild.Core;
using PartialBuild.MSBuild;

namespace PartialBuild.Server;

public sealed class BuildSession
{
    private const int RecentLogLimit = 1000;
    private readonly object recentLogsLock = new();

    public required string Id { get; init; }

    public required bool IsBootstrap { get; init; }

    public required BuildContextOptions Context { get; init; }

    public BuildPlan? Plan { get; set; }

    public BuildExecutionState State { get; set; } = BuildExecutionState.Pending;

    public string? ErrorMessage { get; set; }

    public string? LogFilePath { get; set; }

    public DateTimeOffset CreatedAt { get; init; } = DateTimeOffset.UtcNow;

    public DateTimeOffset? StartedAt { get; set; }

    public DateTimeOffset? CompletedAt { get; set; }

    public CancellationTokenSource Cancellation { get; } = new();

    public ConcurrentDictionary<string, ProjectBuildStatusSnapshot> Projects { get; } =
        new(StringComparer.OrdinalIgnoreCase);

    public List<BuildLogEvent> RecentLogs { get; } = new();

    public SemaphoreSlim LogWriteLock { get; } = new(1, 1);

    public void AddRecentLog(BuildLogEvent logEvent)
    {
        lock (recentLogsLock)
        {
            RecentLogs.Add(logEvent);

            if (RecentLogs.Count > RecentLogLimit)
            {
                RecentLogs.RemoveRange(0, RecentLogs.Count - RecentLogLimit);
            }
        }
    }

    public List<BuildLogEvent> GetRecentLogs()
    {
        lock (recentLogsLock)
        {
            return [.. RecentLogs];
        }
    }

    public BuildSessionSnapshot CreateSnapshot()
    {
        return new BuildSessionSnapshot
        {
            Id = Id,
            IsBootstrap = IsBootstrap,
            State = State,
            Context = Context,
            Plan = Plan,
            Projects = [.. Projects.Values.OrderBy(project => project.ProjectName, StringComparer.OrdinalIgnoreCase)],
            RecentLogs = GetRecentLogs(),
            LogFilePath = LogFilePath,
            ErrorMessage = ErrorMessage,
            CreatedAt = CreatedAt,
            StartedAt = StartedAt,
            CompletedAt = CompletedAt
        };
    }
}
