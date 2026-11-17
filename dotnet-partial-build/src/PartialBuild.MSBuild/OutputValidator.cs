using Microsoft.Build.Evaluation;
using PartialBuild.Core;

namespace PartialBuild.MSBuild;

public sealed class OutputValidator(ProjectOutputLocator outputLocator)
{
    private readonly ProjectOutputLocator outputLocator = outputLocator;

    public void ValidateReusedOutputs(BuildPlan plan)
    {
        var projectCollections = new Dictionary<string, ProjectCollection>(StringComparer.OrdinalIgnoreCase);

        try
        {
            foreach (var reusedProject in plan.ReusedProjects)
            {
                try
                {
                    var properties = plan.Context.GetReferencedProjectGlobalProperties();

                    if (!string.IsNullOrWhiteSpace(reusedProject.TargetFramework))
                    {
                        properties["TargetFramework"] = reusedProject.TargetFramework;
                    }

                    var collectionKey = string.Join(
                        "|",
                        properties
                            .OrderBy(property => property.Key, StringComparer.OrdinalIgnoreCase)
                            .Select(property => $"{property.Key}={property.Value}"));

                    if (!projectCollections.TryGetValue(collectionKey, out var projectCollection))
                    {
                        projectCollection = new ProjectCollection();
                        projectCollections[collectionKey] = projectCollection;
                    }

                    reusedProject.ExpectedOutputPath = outputLocator.FindExpectedOutputPath(
                        reusedProject.ProjectPath,
                        properties,
                        projectCollection);

                    if (string.IsNullOrWhiteSpace(reusedProject.ExpectedOutputPath))
                    {
                        reusedProject.ValidationError =
                            $"Could not determine the expected output path for '{reusedProject.ProjectPath}'.";
                    }
                    else if (!File.Exists(reusedProject.ExpectedOutputPath))
                    {
                        reusedProject.ValidationError =
                            $"Expected output '{reusedProject.ExpectedOutputPath}' does not exist. Run a full bootstrap build or select this project for rebuilding.";
                    }
                    else
                    {
                        reusedProject.ValidationError = null;
                    }
                }
                catch (Exception exception)
                {
                    reusedProject.ValidationError =
                        $"Could not validate '{reusedProject.ProjectPath}': {exception.Message}";
                }
            }
        }
        finally
        {
            foreach (var projectCollection in projectCollections.Values)
            {
                projectCollection.UnloadAllProjects();
            }
        }
    }
}
