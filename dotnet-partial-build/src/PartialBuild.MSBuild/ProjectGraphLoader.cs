using Microsoft.Build.Evaluation;
using Microsoft.Build.Exceptions;
using Microsoft.Build.Graph;
using PartialBuild.Core;

namespace PartialBuild.MSBuild;

public sealed class ProjectGraphLoader
{
    public PartialProjectGraph Load(
        BuildContextOptions context,
        string? progressId = null,
        IProgress<ProjectGraphLoadProgress>? progress = null)
    {
        var normalizedContext = context.CreateNormalizedCopy();
        var rootProjectPath = normalizedContext.GetNormalizedRootProjectPath();

        if (!File.Exists(rootProjectPath))
        {
            throw new FileNotFoundException($"Root project '{rootProjectPath}' was not found.", rootProjectPath);
        }

        var graph = new PartialProjectGraph(normalizedContext);
        var projectCollection = new ProjectCollection();
        var pendingProjects = new Queue<string>();
        var discoveredProjects = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        pendingProjects.Enqueue(rootProjectPath);
        discoveredProjects.Add(rootProjectPath);

        ReportProgress(
            ProjectGraphLoadStage.Started,
            rootProjectPath,
            "Starting project graph discovery.");

        try
        {
            while (pendingProjects.Count > 0)
            {
                var projectPath = pendingProjects.Dequeue();

                if (graph.Nodes.ContainsKey(projectPath))
                {
                    continue;
                }

                if (!File.Exists(projectPath))
                {
                    throw new FileNotFoundException($"Referenced project '{projectPath}' was not found.", projectPath);
                }

                ReportProgress(
                    ProjectGraphLoadStage.Loading,
                    projectPath,
                    $"Loading {Path.GetFileName(projectPath)}");

                var projectGlobalProperties = string.Equals(
                    projectPath,
                    rootProjectPath,
                    StringComparison.OrdinalIgnoreCase)
                    ? normalizedContext.GetGlobalProperties()
                    : normalizedContext.GetReferencedProjectGlobalProperties();

                var project = new Project(
                    projectPath,
                    projectGlobalProperties,
                    toolsVersion: null,
                    projectCollection: projectCollection);

                var rootDirectory = Path.GetDirectoryName(rootProjectPath) ?? Directory.GetCurrentDirectory();
                var dependencies = new List<string>();
                var seenDependencies = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

                foreach (var reference in project.GetItems("ProjectReference"))
                {
                    if (string.IsNullOrWhiteSpace(reference.EvaluatedInclude))
                    {
                        continue;
                    }

                    var dependencyPath = BuildContextOptions.NormalizeProjectPath(
                        Path.Combine(project.DirectoryPath, reference.EvaluatedInclude));

                    if (seenDependencies.Add(dependencyPath))
                    {
                        dependencies.Add(dependencyPath);
                    }

                    if (!graph.Nodes.ContainsKey(dependencyPath) && discoveredProjects.Add(dependencyPath))
                    {
                        pendingProjects.Enqueue(dependencyPath);
                    }
                }

                graph.Nodes[projectPath] = new PartialProjectGraphNode
                {
                    FullPath = projectPath,
                    Name = Path.GetFileNameWithoutExtension(projectPath),
                    RelativePath = Path.GetRelativePath(rootDirectory, projectPath),
                    TargetFramework = string.IsNullOrWhiteSpace(project.GetPropertyValue("TargetFramework"))
                        ? null
                        : project.GetPropertyValue("TargetFramework")
                };

                graph.Nodes[projectPath].Dependencies.AddRange(dependencies);

                ReportProgress(
                    ProjectGraphLoadStage.Loaded,
                    projectPath,
                    $"Loaded {Path.GetFileName(projectPath)} with {dependencies.Count} project references.");
            }

            BuildReverseEdges(graph);
            EnsureNoCycles(graph);

            ReportProgress(
                ProjectGraphLoadStage.Completed,
                rootProjectPath,
                $"Project graph completed with {graph.Nodes.Count} projects.");

            return graph;
        }
        catch (InvalidProjectFileException exception)
        {
            ReportProgress(
                ProjectGraphLoadStage.Failed,
                rootProjectPath,
                exception.Message);

            throw new InvalidOperationException(exception.Message, exception);
        }
        catch (Exception exception)
        {
            ReportProgress(
                ProjectGraphLoadStage.Failed,
                rootProjectPath,
                exception.Message);

            throw;
        }
        finally
        {
            projectCollection.UnloadAllProjects();
        }

        void ReportProgress(ProjectGraphLoadStage stage, string projectPath, string message)
        {
            progress?.Report(new ProjectGraphLoadProgress
            {
                ProgressId = progressId ?? string.Empty,
                Stage = stage,
                ProjectPath = projectPath,
                ProjectName = Path.GetFileNameWithoutExtension(projectPath),
                LoadedCount = graph.Nodes.Count,
                DiscoveredCount = discoveredProjects.Count,
                PendingCount = pendingProjects.Count,
                Message = message,
                Timestamp = DateTimeOffset.UtcNow
            });
        }
    }

    private static void BuildReverseEdges(PartialProjectGraph graph)
    {
        foreach (var node in graph.Nodes.Values)
        {
            node.Dependents.Clear();
        }

        foreach (var node in graph.Nodes.Values)
        {
            foreach (var dependency in node.Dependencies)
            {
                var dependencyNode = graph.GetRequiredNode(dependency);

                if (!dependencyNode.Dependents.Any(path => string.Equals(path, node.FullPath, StringComparison.OrdinalIgnoreCase)))
                {
                    dependencyNode.Dependents.Add(node.FullPath);
                }
            }
        }
    }

    private static void EnsureNoCycles(PartialProjectGraph graph)
    {
        var states = new Dictionary<string, int>(StringComparer.OrdinalIgnoreCase);
        var pathStack = new List<string>();

        foreach (var projectPath in graph.Nodes.Keys.OrderBy(path => path, StringComparer.OrdinalIgnoreCase))
        {
            Visit(projectPath);
        }

        void Visit(string projectPath)
        {
            if (states.TryGetValue(projectPath, out var state))
            {
                if (state == 1)
                {
                    var cycleStart = pathStack.FindIndex(path => string.Equals(path, projectPath, StringComparison.OrdinalIgnoreCase));
                    var cycle = pathStack.Skip(Math.Max(cycleStart, 0)).Append(projectPath);
                    throw new InvalidOperationException($"Project reference cycle detected: {string.Join(" -> ", cycle)}");
                }

                return;
            }

            states[projectPath] = 1;
            pathStack.Add(projectPath);

            foreach (var dependency in graph.GetRequiredNode(projectPath).Dependencies)
            {
                Visit(dependency);
            }

            pathStack.RemoveAt(pathStack.Count - 1);
            states[projectPath] = 2;
        }
    }
}
