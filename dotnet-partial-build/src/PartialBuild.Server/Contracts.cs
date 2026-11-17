using PartialBuild.Core;

namespace PartialBuild.Server;

public sealed class GraphLoadRequest
{
    public BuildContextOptions Context { get; set; } = new();

    public string? ProgressId { get; set; }
}

public sealed class GraphProjectResponse
{
    public required string FullPath { get; init; }

    public required string Name { get; init; }

    public required string RelativePath { get; init; }

    public required List<string> Dependencies { get; init; }

    public required List<string> Dependents { get; init; }
}

public sealed class GraphResponse
{
    public required string RootProjectPath { get; init; }

    public required List<GraphProjectResponse> Projects { get; init; }
}

public class BuildPlanRequest
{
    public BuildContextOptions Context { get; set; } = new();

    public List<string> RebuildProjectPaths { get; set; } = new();

    public bool ValidateOutputs { get; set; } = true;
}

public sealed class BuildPlanResponse
{
    public required BuildPlan Plan { get; init; }

    public required bool CanStart { get; init; }

    public required List<string> ValidationErrors { get; init; }
}

public sealed class StartBuildRequest : BuildPlanRequest
{
}

public sealed class BootstrapBuildRequest
{
    public BuildContextOptions Context { get; set; } = new();
}
