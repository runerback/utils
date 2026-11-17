namespace PartialBuild.Core;

public sealed class PartialProjectGraphNode
{
    public required string FullPath { get; init; }

    public required string Name { get; init; }

    public required string RelativePath { get; init; }

    public string? TargetFramework { get; init; }

    public List<string> Dependencies { get; } = new();

    public List<string> Dependents { get; } = new();
}

public sealed class PartialProjectGraph(BuildContextOptions context)
{

    public BuildContextOptions Context { get; } = context;

    public string RootProjectPath { get; } = context.GetNormalizedRootProjectPath();

    public Dictionary<string, PartialProjectGraphNode> Nodes { get; } = new(StringComparer.OrdinalIgnoreCase);

    public PartialProjectGraphNode GetRequiredNode(string projectPath)
    {
        var normalizedPath = BuildContextOptions.NormalizeProjectPath(projectPath);

        if (!Nodes.TryGetValue(normalizedPath, out var node))
        {
            throw new InvalidOperationException($"Project '{projectPath}' is not reachable from '{RootProjectPath}'.");
        }

        return node;
    }
}
