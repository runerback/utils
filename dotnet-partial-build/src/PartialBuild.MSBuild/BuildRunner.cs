using System.Diagnostics;
using PartialBuild.Core;

namespace PartialBuild.MSBuild;

public sealed class BuildRunner
{
    private static readonly string[] MsBuildLocatorEnvironmentVariables =
    [
        "MSBUILD_EXE_PATH",
        "MSBuildSDKsPath",
        "MSBuildExtensionsPath",
        "MSBuildExtensionsPath32",
        "MSBuildToolsPath",
        "MSBuildToolsRoot",
        "MSBuildFrameworkToolsPath",
        "MSBuildFrameworkToolsPath32",
        "MSBuildRuntimeType"
    ];
    public async Task<BuildRunResult> RunPlanAsync(
        BuildPlan plan,
        string buildId,
        Func<ProjectBuildStatusChangedEvent, Task> onProjectStatus,
        Func<BuildLogEvent, Task> onLog,
        CancellationToken cancellationToken)
    {
        var context = plan.Context.CreateNormalizedCopy();
        var rootProjectPath = context.GetNormalizedRootProjectPath();
        var rootDirectory = Path.GetDirectoryName(rootProjectPath) ?? Directory.GetCurrentDirectory();

        await onLog(CreateLog(
            buildId,
            null,
            null,
            BuildLogStream.StandardOutput,
            $"Build context: Configuration={context.Configuration}; TargetFramework={context.TargetFramework ?? "<all>"}; RuntimeIdentifier={context.RuntimeIdentifier ?? "<none>"}; Platform={context.Platform ?? "<default>"}; RestoreBeforeBuild={context.RestoreBeforeBuild}"));

        await onLog(CreateLog(
            buildId,
            null,
            null,
            BuildLogStream.StandardOutput,
            $"Build plan: {plan.Projects.Count} rebuild; {plan.ReusedProjects.Count} reuse."));

        if (context.RestoreBeforeBuild)
        {
            await onLog(CreateLog(
                buildId,
                null,
                null,
                BuildLogStream.StandardOutput,
                $"Restoring root dependency graph without a target-framework override: {rootProjectPath}"));

            var restoreExitCode = await RunDotNetAsync(
                buildId,
                null,
                null,
                CreateRestoreArguments(context),
                rootDirectory,
                onLog,
                cancellationToken);

            if (restoreExitCode != 0)
            {
                return new BuildRunResult
                {
                    Succeeded = false,
                    ExitCode = restoreExitCode,
                    ErrorMessage = $"Restore failed with exit code {restoreExitCode}."
                };
            }
        }

        for (var index = 0; index < plan.Projects.Count; index++)
        {
            var project = plan.Projects[index];

            await onProjectStatus(new ProjectBuildStatusChangedEvent
            {
                BuildId = buildId,
                ProjectPath = project.ProjectPath,
                ProjectName = project.ProjectName,
                State = ProjectExecutionState.Building
            });

            var exitCode = await RunDotNetAsync(
                buildId,
                project.ProjectPath,
                project.ProjectName,
                CreatePartialBuildArguments(project, context),
                Path.GetDirectoryName(project.ProjectPath) ?? rootDirectory,
                onLog,
                cancellationToken);

            if (exitCode == 0)
            {
                await onProjectStatus(new ProjectBuildStatusChangedEvent
                {
                    BuildId = buildId,
                    ProjectPath = project.ProjectPath,
                    ProjectName = project.ProjectName,
                    State = ProjectExecutionState.Succeeded
                });

                continue;
            }

            var errorMessage = $"Build failed for '{project.ProjectPath}' with exit code {exitCode}.";

            await onProjectStatus(new ProjectBuildStatusChangedEvent
            {
                BuildId = buildId,
                ProjectPath = project.ProjectPath,
                ProjectName = project.ProjectName,
                State = ProjectExecutionState.Failed,
                Message = errorMessage
            });

            foreach (var remainingProject in plan.Projects.Skip(index + 1))
            {
                await onProjectStatus(new ProjectBuildStatusChangedEvent
                {
                    BuildId = buildId,
                    ProjectPath = remainingProject.ProjectPath,
                    ProjectName = remainingProject.ProjectName,
                    State = ProjectExecutionState.Skipped,
                    Message = "Skipped because an earlier project failed."
                });
            }

            return new BuildRunResult
            {
                Succeeded = false,
                ExitCode = exitCode,
                ErrorMessage = errorMessage
            };
        }

        return new BuildRunResult
        {
            Succeeded = true,
            ExitCode = 0
        };
    }

    public async Task<BuildRunResult> RunFullBuildAsync(
        BuildContextOptions contextOptions,
        string buildId,
        Func<ProjectBuildStatusChangedEvent, Task> onProjectStatus,
        Func<BuildLogEvent, Task> onLog,
        CancellationToken cancellationToken)
    {
        var context = contextOptions.CreateNormalizedCopy();
        context.RestoreBeforeBuild = true;

        var rootProjectPath = context.GetNormalizedRootProjectPath();
        var rootName = Path.GetFileNameWithoutExtension(rootProjectPath);
        var rootDirectory = Path.GetDirectoryName(rootProjectPath) ?? Directory.GetCurrentDirectory();

        await onLog(CreateLog(
            buildId,
            rootProjectPath,
            rootName,
            BuildLogStream.StandardOutput,
            $"Build context: Configuration={context.Configuration}; TargetFramework={context.TargetFramework ?? "<all>"}; RuntimeIdentifier={context.RuntimeIdentifier ?? "<none>"}; Platform={context.Platform ?? "<default>"}; RestoreBeforeBuild={context.RestoreBeforeBuild}"));

        await onProjectStatus(new ProjectBuildStatusChangedEvent
        {
            BuildId = buildId,
            ProjectPath = rootProjectPath,
            ProjectName = rootName,
            State = ProjectExecutionState.Building,
            Message = "Full bootstrap build"
        });

        await onLog(CreateLog(
            buildId,
            null,
            null,
            BuildLogStream.StandardOutput,
            $"Restoring full dependency graph without a target-framework override: {rootProjectPath}"));

        var restoreExitCode = await RunDotNetAsync(
            buildId,
            null,
            null,
            CreateRestoreArguments(context),
            rootDirectory,
            onLog,
            cancellationToken);

        if (restoreExitCode != 0)
        {
            var restoreErrorMessage = $"Full bootstrap restore failed with exit code {restoreExitCode}.";

            await onProjectStatus(new ProjectBuildStatusChangedEvent
            {
                BuildId = buildId,
                ProjectPath = rootProjectPath,
                ProjectName = rootName,
                State = ProjectExecutionState.Failed,
                Message = restoreErrorMessage
            });

            return new BuildRunResult
            {
                Succeeded = false,
                ExitCode = restoreExitCode,
                ErrorMessage = restoreErrorMessage
            };
        }

        var exitCode = await RunDotNetAsync(
            buildId,
            rootProjectPath,
            rootName,
            CreateFullBuildArguments(context),
            rootDirectory,
            onLog,
            cancellationToken);

        var state = exitCode == 0 ? ProjectExecutionState.Succeeded : ProjectExecutionState.Failed;
        var message = exitCode == 0 ? null : $"Full bootstrap build failed with exit code {exitCode}.";

        await onProjectStatus(new ProjectBuildStatusChangedEvent
        {
            BuildId = buildId,
            ProjectPath = rootProjectPath,
            ProjectName = rootName,
            State = state,
            Message = message
        });

        return new BuildRunResult
        {
            Succeeded = exitCode == 0,
            ExitCode = exitCode,
            ErrorMessage = message
        };
    }

    private async Task<int> RunDotNetAsync(
        string buildId,
        string? projectPath,
        string? projectName,
        IReadOnlyList<string> arguments,
        string workingDirectory,
        Func<BuildLogEvent, Task> onLog,
        CancellationToken cancellationToken)
    {
        var startInfo = new ProcessStartInfo
        {
            FileName = "dotnet",
            WorkingDirectory = workingDirectory,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true
        };

        foreach (var argument in arguments)
        {
            startInfo.ArgumentList.Add(argument);
        }

        RemoveMsBuildLocatorEnvironment(startInfo);

        await onLog(CreateLog(
            buildId,
            projectPath,
            projectName,
            BuildLogStream.StandardOutput,
            $"> dotnet {string.Join(' ', arguments)}"));

        using var process = new Process { StartInfo = startInfo, EnableRaisingEvents = true };
        process.Start();

        var standardOutputTask = ReadStreamAsync(
            process.StandardOutput,
            buildId,
            projectPath,
            projectName,
            BuildLogStream.StandardOutput,
            onLog);

        var standardErrorTask = ReadStreamAsync(
            process.StandardError,
            buildId,
            projectPath,
            projectName,
            BuildLogStream.StandardError,
            onLog);

        try
        {
            await process.WaitForExitAsync(cancellationToken);
            await Task.WhenAll(standardOutputTask, standardErrorTask);

            if (process.ExitCode != 0)
            {
                await onLog(CreateLog(
                    buildId,
                    projectPath,
                    projectName,
                    BuildLogStream.StandardError,
                    $"[diagnostics] ExitCode={process.ExitCode}; WorkingDirectory={workingDirectory}; Command=dotnet {string.Join(' ', arguments)}"));
            }

            return process.ExitCode;
        }
        catch (OperationCanceledException)
        {
            if (!process.HasExited)
            {
                process.Kill(entireProcessTree: true);
            }

            await process.WaitForExitAsync(CancellationToken.None);
            await Task.WhenAll(standardOutputTask, standardErrorTask);
            throw;
        }
    }

    private static void RemoveMsBuildLocatorEnvironment(ProcessStartInfo startInfo)
    {
        foreach (var variable in MsBuildLocatorEnvironmentVariables)
        {
            startInfo.Environment.Remove(variable);
        }
    }

    private static async Task ReadStreamAsync(
        StreamReader reader,
        string buildId,
        string? projectPath,
        string? projectName,
        BuildLogStream stream,
        Func<BuildLogEvent, Task> onLog)
    {
        string? line;

        while ((line = await reader.ReadLineAsync()) is not null)
        {
            await onLog(CreateLog(buildId, projectPath, projectName, stream, line));
        }
    }

    private static BuildLogEvent CreateLog(
        string buildId,
        string? projectPath,
        string? projectName,
        BuildLogStream stream,
        string message)
    {
        return new BuildLogEvent
        {
            BuildId = buildId,
            ProjectPath = projectPath,
            ProjectName = projectName,
            Stream = stream,
            Message = message,
            Timestamp = DateTimeOffset.UtcNow
        };
    }

    private static List<string> CreatePartialBuildArguments(PlannedProject project, BuildContextOptions context)
    {
        var arguments = new List<string>
        {
            "build",
            project.ProjectPath,
            "--nologo",
            "--no-dependencies",
            "--no-restore",
            "--verbosity",
            "minimal",
            "--configuration",
            context.Configuration
        };

        AddBuildContextArguments(
            arguments,
            context,
            includeRestoreRuntime: false,
            targetFramework: project.TargetFramework);
        arguments.Add("-p:BuildProjectReferences=false");
        return arguments;
    }

    private static List<string> CreateFullBuildArguments(BuildContextOptions context)
    {
        var arguments = new List<string>
        {
            "build",
            context.GetNormalizedRootProjectPath(),
            "--nologo",
            "--verbosity",
            "normal",
            "--configuration",
            context.Configuration
        };

        arguments.Add("--no-restore");

        AddBuildContextArguments(
            arguments,
            context,
            includeRestoreRuntime: false,
            targetFramework: context.TargetFramework);
        return arguments;
    }

    private static List<string> CreateRestoreArguments(BuildContextOptions context)
    {
        var arguments = new List<string>
        {
            "restore",
            context.GetNormalizedRootProjectPath(),
            "--nologo",
            "--verbosity",
            "minimal"
        };

        AddBuildContextArguments(
            arguments,
            context,
            includeRestoreRuntime: true,
            targetFramework: null);
        return arguments;
    }

    private static void AddBuildContextArguments(
        List<string> arguments,
        BuildContextOptions context,
        bool includeRestoreRuntime,
        string? targetFramework)
    {
        if (!string.IsNullOrWhiteSpace(targetFramework))
        {
            if (includeRestoreRuntime)
            {
                arguments.Add($"-p:TargetFramework={targetFramework}");
            }
            else
            {
                arguments.Add("--framework");
                arguments.Add(targetFramework);
            }
        }

        if (!string.IsNullOrWhiteSpace(context.RuntimeIdentifier))
        {
            if (includeRestoreRuntime)
            {
                arguments.Add("--runtime");
                arguments.Add(context.RuntimeIdentifier);
            }
            else
            {
                arguments.Add("--runtime");
                arguments.Add(context.RuntimeIdentifier);
            }
        }

        if (!string.IsNullOrWhiteSpace(context.Platform))
        {
            arguments.Add($"-p:Platform={context.Platform}");
        }

        arguments.Add($"-p:Configuration={context.Configuration}");

        foreach (var property in context.AdditionalProperties)
        {
            if (string.IsNullOrWhiteSpace(property.Key))
            {
                continue;
            }

            arguments.Add($"-p:{property.Key}={property.Value}");
        }
    }
}
