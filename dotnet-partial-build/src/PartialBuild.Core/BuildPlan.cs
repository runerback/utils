namespace PartialBuild.Core;

public sealed class PlannedProject
{
    public required string ProjectPath { get; init; }

    public required string ProjectName { get; init; }

    public required string RelativePath { get; init; }

    public string? TargetFramework { get; init; }

    public int Order { get; set; }

    public required string Reason { get; set; }

    public bool IsExplicitlySelected { get; init; }

    public bool IsRoot { get; init; }
}

public sealed class ReusedProject
{
    public required string ProjectPath { get; init; }

    public required string ProjectName { get; init; }

    public required string RelativePath { get; init; }

    public string? TargetFramework { get; init; }

    public string? ExpectedOutputPath { get; set; }

    public string? ValidationError { get; set; }
}

public sealed class BuildPlan
{
    public required BuildContextOptions Context { get; init; }

    public required string RootProjectPath { get; init; }

    public List<PlannedProject> Projects { get; } = new();

    public List<ReusedProject> ReusedProjects { get; } = new();

    public bool HasValidationErrors => ReusedProjects.Any(project => project.ValidationError is not null);
}
