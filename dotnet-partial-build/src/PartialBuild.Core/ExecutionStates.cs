namespace PartialBuild.Core;

public enum BuildExecutionState
{
    Pending,
    Running,
    Succeeded,
    Failed,
    Cancelled
}

public enum ProjectExecutionState
{
    Queued,
    Reused,
    Building,
    Succeeded,
    Failed,
    Skipped,
    Cancelled
}
