namespace PartialBuild.Core;

public sealed class RebuildPlanner
{
    public BuildPlan CreatePlan(PartialProjectGraph graph, IEnumerable<string> selectedProjectPaths)
    {
        var explicitlySelected = selectedProjectPaths
            .Where(path => !string.IsNullOrWhiteSpace(path))
            .Select(BuildContextOptions.NormalizeProjectPath)
            .ToHashSet(StringComparer.OrdinalIgnoreCase);

        foreach (var selectedPath in explicitlySelected)
        {
            graph.GetRequiredNode(selectedPath);
        }

        var requiredProjects = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            graph.RootProjectPath
        };

        var pending = new Stack<string>();

        foreach (var selectedPath in explicitlySelected)
        {
            pending.Push(selectedPath);
        }

        while (pending.Count > 0)
        {
            var current = pending.Pop();

            if (!requiredProjects.Add(current))
            {
                continue;
            }

            foreach (var dependent in graph.GetRequiredNode(current).Dependents)
            {
                pending.Push(dependent);
            }
        }

        var orderedProjects = CreateTopologicalOrder(graph, requiredProjects);
        var selectedDependencyNames = CreateSelectedDependencyLookup(graph, explicitlySelected);
        var plan = new BuildPlan
        {
            Context = graph.Context,
            RootProjectPath = graph.RootProjectPath
        };

        for (var index = 0; index < orderedProjects.Count; index++)
        {
            var projectPath = orderedProjects[index];
            var node = graph.GetRequiredNode(projectPath);
            var isRoot = string.Equals(projectPath, graph.RootProjectPath, StringComparison.OrdinalIgnoreCase);
            var isExplicit = explicitlySelected.Contains(projectPath);
            var reason = CreateReason(graph, node, isRoot, isExplicit, selectedDependencyNames);

            plan.Projects.Add(new PlannedProject
            {
                ProjectPath = node.FullPath,
                ProjectName = node.Name,
                RelativePath = node.RelativePath,
                TargetFramework = node.TargetFramework,
                Order = index + 1,
                Reason = reason,
                IsExplicitlySelected = isExplicit,
                IsRoot = isRoot
            });
        }

        foreach (var node in graph.Nodes.Values
                     .Where(node => !requiredProjects.Contains(node.FullPath))
                     .OrderBy(node => node.RelativePath, StringComparer.OrdinalIgnoreCase))
        {
            plan.ReusedProjects.Add(new ReusedProject
            {
                ProjectPath = node.FullPath,
                ProjectName = node.Name,
                RelativePath = node.RelativePath,
                TargetFramework = node.TargetFramework
            });
        }

        return plan;
    }

    private static List<string> CreateTopologicalOrder(PartialProjectGraph graph, HashSet<string> requiredProjects)
    {
        var result = new List<string>();
        var visitStates = new Dictionary<string, int>(StringComparer.OrdinalIgnoreCase);

        foreach (var projectPath in requiredProjects.OrderBy(path => path, StringComparer.OrdinalIgnoreCase))
        {
            Visit(projectPath);
        }

        return result;

        void Visit(string projectPath)
        {
            if (visitStates.TryGetValue(projectPath, out var state))
            {
                if (state == 1)
                {
                    throw new InvalidOperationException($"A project reference cycle was detected at '{projectPath}'.");
                }

                return;
            }

            visitStates[projectPath] = 1;

            foreach (var dependency in graph.GetRequiredNode(projectPath).Dependencies)
            {
                if (requiredProjects.Contains(dependency))
                {
                    Visit(dependency);
                }
            }

            visitStates[projectPath] = 2;
            result.Add(projectPath);
        }
    }

    private static Dictionary<string, List<string>> CreateSelectedDependencyLookup(
        PartialProjectGraph graph,
        HashSet<string> explicitlySelected)
    {
        var lookup = new Dictionary<string, List<string>>(StringComparer.OrdinalIgnoreCase);

        foreach (var projectPath in graph.Nodes.Keys)
        {
            FindSelectedDependencies(projectPath, new HashSet<string>(StringComparer.OrdinalIgnoreCase));
        }

        return lookup;

        List<string> FindSelectedDependencies(string projectPath, HashSet<string> visiting)
        {
            if (lookup.TryGetValue(projectPath, out var cached))
            {
                return cached;
            }

            if (!visiting.Add(projectPath))
            {
                return new List<string>();
            }

            var selectedDependencies = new List<string>();

            foreach (var dependency in graph.GetRequiredNode(projectPath).Dependencies)
            {
                if (explicitlySelected.Contains(dependency))
                {
                    selectedDependencies.Add(graph.GetRequiredNode(dependency).Name);
                }

                selectedDependencies.AddRange(FindSelectedDependencies(dependency, visiting));
            }

            visiting.Remove(projectPath);

            var distinct = selectedDependencies
                .Distinct(StringComparer.OrdinalIgnoreCase)
                .OrderBy(name => name, StringComparer.OrdinalIgnoreCase)
                .ToList();

            lookup[projectPath] = distinct;
            return distinct;
        }
    }

    private static string CreateReason(
        PartialProjectGraph graph,
        PartialProjectGraphNode node,
        bool isRoot,
        bool isExplicitlySelected,
        Dictionary<string, List<string>> selectedDependencyNames)
    {
        if (isRoot)
        {
            return "Root output project";
        }

        if (isExplicitlySelected)
        {
            return "Explicitly selected";
        }

        if (selectedDependencyNames.TryGetValue(node.FullPath, out var selectedDependencies) &&
            selectedDependencies.Count > 0)
        {
            return $"Depends on {string.Join(", ", selectedDependencies)}";
        }

        return $"Required by {graph.GetRequiredNode(graph.RootProjectPath).Name}";
    }
}
