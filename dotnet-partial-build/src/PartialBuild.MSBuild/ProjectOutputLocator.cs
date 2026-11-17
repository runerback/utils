using Microsoft.Build.Evaluation;
using PartialBuild.Core;

namespace PartialBuild.MSBuild;

public sealed class ProjectOutputLocator
{
    public string? FindExpectedOutputPath(string projectPath, BuildContextOptions context)
    {
        var projectCollection = new ProjectCollection(context.GetGlobalProperties());

        try
        {
            return FindExpectedOutputPath(
                projectPath,
                context.GetGlobalProperties(),
                projectCollection);
        }
        finally
        {
            projectCollection.UnloadAllProjects();
        }
    }

    public string? FindExpectedOutputPath(
        string projectPath,
        Dictionary<string, string> globalProperties,
        ProjectCollection projectCollection)
    {
        var project = projectCollection.LoadProject(
            projectPath,
            globalProperties,
            toolsVersion: null);

        var targetPath = project.GetPropertyValue("TargetPath");

        if (!string.IsNullOrWhiteSpace(targetPath))
        {
            return MakeAbsolute(project.DirectoryPath, targetPath);
        }

        var targetDirectory = project.GetPropertyValue("TargetDir");
        var targetFileName = project.GetPropertyValue("TargetFileName");

        if (!string.IsNullOrWhiteSpace(targetDirectory) && !string.IsNullOrWhiteSpace(targetFileName))
        {
            return MakeAbsolute(project.DirectoryPath, Path.Combine(targetDirectory, targetFileName));
        }

        var outputPath = project.GetPropertyValue("OutputPath");
        var assemblyName = project.GetPropertyValue("AssemblyName");
        var targetExtension = project.GetPropertyValue("TargetExt");

        if (string.IsNullOrWhiteSpace(assemblyName))
        {
            assemblyName = Path.GetFileNameWithoutExtension(projectPath);
        }

        if (string.IsNullOrWhiteSpace(targetExtension))
        {
            targetExtension = ".dll";
        }

        if (string.IsNullOrWhiteSpace(outputPath))
        {
            return null;
        }

        return MakeAbsolute(
            project.DirectoryPath,
            Path.Combine(outputPath, assemblyName + targetExtension));
    }

    private static string MakeAbsolute(string baseDirectory, string path)
    {
        return Path.GetFullPath(Path.IsPathRooted(path) ? path : Path.Combine(baseDirectory, path));
    }
}
