using System.Collections.Concurrent;
using Microsoft.AspNetCore.SignalR;
using PartialBuild.Core;
using PartialBuild.MSBuild;

namespace PartialBuild.Server;

public sealed class BuildSessionManager(
    ProjectGraphLoader graphLoader,
    RebuildPlanner rebuildPlanner,
    OutputValidator outputValidator,
    BuildRunner buildRunner,
    IHubContext<BuildHub> hubContext,
    IWebHostEnvironment environment,
    ILogger<BuildSessionManager> logger)
{
    private readonly ProjectGraphLoader graphLoader = graphLoader;
    private readonly RebuildPlanner rebuildPlanner = rebuildPlanner;
    private readonly OutputValidator outputValidator = outputValidator;
    private readonly BuildRunner buildRunner = buildRunner;
    private readonly IHubContext<BuildHub> hubContext = hubContext;
    private readonly IWebHostEnvironment environment = environment;
    private readonly ILogger<BuildSessionManager> logger = logger;
    private readonly ConcurrentDictionary<string, BuildSession> sessions = new(StringComparer.OrdinalIgnoreCase);
    private readonly ConcurrentDictionary<string, SemaphoreSlim> contextGates = new(StringComparer.OrdinalIgnoreCase);
    private readonly ConcurrentDictionary<string, CachedProjectGraph> graphCache = new(StringComparer.OrdinalIgnoreCase);
    private static readonly TimeSpan GraphCacheLifetime = TimeSpan.FromMinutes(2);

    public async Task<PartialProjectGraph> LoadGraphAsync(BuildContextOptions context, string? progressId = null)
    {
        logger.LogInformation("Loading project graph for {RootProjectPath}.", context.RootProjectPath);

        try
        {
            PartialProjectGraph graph;

            if (string.IsNullOrWhiteSpace(progressId))
            {
                graph = await Task.Run(() => graphLoader.Load(context));
            }
            else
            {
                var progress = new SignalRGraphLoadProgress(progressId, hubContext);
                graph = await Task.Run(() => graphLoader.Load(context, progressId, progress));
            }

            logger.LogInformation(
                "Loaded project graph for {RootProjectPath}: {ProjectCount} projects.",
                graph.RootProjectPath,
                graph.Nodes.Count);

            graphCache[graph.Context.GetContextKey()] = new CachedProjectGraph(graph, DateTimeOffset.UtcNow);

            return graph;
        }
        catch (Exception exception)
        {
            logger.LogError(
                exception,
                "Failed to load project graph for {RootProjectPath}.",
                context.RootProjectPath);
            throw;
        }
    }

    public async Task<BuildPlan> CreatePlanAsync(BuildPlanRequest request)
    {
        logger.LogInformation(
            "Creating build plan for {RootProjectPath} with {SelectedCount} selected projects.",
            request.Context.RootProjectPath,
            request.RebuildProjectPaths.Count);

        var graph = GetCachedGraph(request.Context);

        if (graph is null)
        {
            graph = await LoadGraphAsync(request.Context);
        }
        else
        {
            logger.LogInformation(
                "Using cached project graph for {RootProjectPath}: {ProjectCount} projects.",
                graph.RootProjectPath,
                graph.Nodes.Count);
        }

        var plan = rebuildPlanner.CreatePlan(graph, request.RebuildProjectPaths);

        if (request.ValidateOutputs)
        {
            outputValidator.ValidateReusedOutputs(plan);
        }

        logger.LogInformation(
            "Build plan for {RootProjectPath}: {RebuildCount} rebuild, {ReuseCount} reuse.",
            plan.RootProjectPath,
            plan.Projects.Count,
            plan.ReusedProjects.Count);

        if (plan.HasValidationErrors)
        {
            var errors = plan.ReusedProjects
                .Where(project => project.ValidationError is not null)
                .Select(project => $"{project.RelativePath}: {project.ValidationError}")
                .ToList();

            logger.LogWarning(
                "Reused-output validation failed for {ErrorCount} projects:{NewLine}{Errors}",
                errors.Count,
                Environment.NewLine,
                string.Join(Environment.NewLine, errors));
        }

        return plan;
    }

    public async Task<BuildSessionSnapshot> StartBuildAsync(StartBuildRequest request)
    {
        var plan = await CreatePlanAsync(request);

        if (plan.HasValidationErrors)
        {
            var errors = plan.ReusedProjects
                .Where(project => project.ValidationError is not null)
                .Select(project => $"{project.RelativePath}: {project.ValidationError}");

            logger.LogWarning(
                "Partial build for {RootProjectPath} was rejected because reused-output validation failed.",
                plan.RootProjectPath);

            throw new BuildValidationException(errors);
        }

        var session = CreateSession(plan.Context, isBootstrap: false);
        session.Plan = plan;

        foreach (var project in plan.Projects)
        {
            session.Projects[project.ProjectPath] = new ProjectBuildStatusSnapshot
            {
                ProjectPath = project.ProjectPath,
                ProjectName = project.ProjectName,
                State = ProjectExecutionState.Queued
            };
        }

        foreach (var project in plan.ReusedProjects)
        {
            session.Projects[project.ProjectPath] = new ProjectBuildStatusSnapshot
            {
                ProjectPath = project.ProjectPath,
                ProjectName = project.ProjectName,
                State = ProjectExecutionState.Reused,
                Message = "Existing compatible output will be reused."
            };
        }

        var gate = AcquireContextGate(session.Context);
        sessions[session.Id] = session;

        _ = Task.Run(() => ExecutePartialBuildAsync(session, gate));

        logger.LogInformation(
            "Started partial build {BuildId} for {RootProjectPath}: {RebuildCount} rebuild, {ReuseCount} reuse. Log file: {LogFilePath}",
            session.Id,
            session.Context.RootProjectPath,
            plan.Projects.Count,
            plan.ReusedProjects.Count,
            session.LogFilePath);

        return session.CreateSnapshot();
    }

    public Task<BuildSessionSnapshot> StartBootstrapAsync(BootstrapBuildRequest request)
    {
        var context = request.Context.CreateNormalizedCopy();

        if (!File.Exists(context.RootProjectPath))
        {
            throw new FileNotFoundException(
                $"Root project '{context.RootProjectPath}' was not found.",
                context.RootProjectPath);
        }

        var session = CreateSession(context, isBootstrap: true);
        var rootName = Path.GetFileNameWithoutExtension(context.RootProjectPath);

        session.Projects[context.RootProjectPath] = new ProjectBuildStatusSnapshot
        {
            ProjectPath = context.RootProjectPath,
            ProjectName = rootName,
            State = ProjectExecutionState.Queued,
            Message = "Full dependency build"
        };

        var gate = AcquireContextGate(session.Context);
        sessions[session.Id] = session;

        _ = Task.Run(() => ExecuteBootstrapAsync(session, gate));

        logger.LogInformation(
            "Started full bootstrap build {BuildId} for {RootProjectPath}. Log file: {LogFilePath}",
            session.Id,
            session.Context.RootProjectPath,
            session.LogFilePath);

        return Task.FromResult(session.CreateSnapshot());
    }

    public BuildSessionSnapshot? GetSession(string buildId)
    {
        return sessions.TryGetValue(buildId, out var session) ? session.CreateSnapshot() : null;
    }

    public string? GetLogFilePath(string buildId)
    {
        return sessions.TryGetValue(buildId, out var session) ? session.LogFilePath : null;
    }

    public bool Cancel(string buildId)
    {
        if (!sessions.TryGetValue(buildId, out var session))
        {
            return false;
        }

        if (session.State is BuildExecutionState.Succeeded or BuildExecutionState.Failed or BuildExecutionState.Cancelled)
        {
            return false;
        }

        session.Cancellation.Cancel();
        logger.LogWarning("Cancellation requested for build {BuildId}.", buildId);
        return true;
    }

    private PartialProjectGraph? GetCachedGraph(BuildContextOptions context)
    {
        var key = context.GetContextKey();

        if (!graphCache.TryGetValue(key, out var cached))
        {
            return null;
        }

        if (DateTimeOffset.UtcNow - cached.LoadedAt > GraphCacheLifetime)
        {
            graphCache.TryRemove(key, out _);
            return null;
        }

        return cached.Graph;
    }

    private BuildSession CreateSession(BuildContextOptions context, bool isBootstrap)
    {
        var id = Guid.NewGuid().ToString("N");
        var logDirectory = Path.Combine(environment.ContentRootPath, "logs");
        Directory.CreateDirectory(logDirectory);

        return new BuildSession
        {
            Id = id,
            IsBootstrap = isBootstrap,
            Context = context,
            LogFilePath = Path.Combine(logDirectory, $"{DateTimeOffset.UtcNow:yyyyMMdd-HHmmss}-{id}.log")
        };
    }

    private SemaphoreSlim AcquireContextGate(BuildContextOptions context)
    {
        var key = context.GetContextKey();
        var gate = contextGates.GetOrAdd(key, _ => new SemaphoreSlim(1, 1));

        if (!gate.Wait(0))
        {
            logger.LogWarning(
                "Rejected build for {RootProjectPath}: another build is already running for this context.",
                context.RootProjectPath);

            throw new InvalidOperationException("A build is already running for this project and build context.");
        }

        return gate;
    }

    private async Task ExecutePartialBuildAsync(BuildSession session, SemaphoreSlim contextGate)
    {
        try
        {
            await BeginSessionAsync(session);

            var result = await buildRunner.RunPlanAsync(
                session.Plan!,
                session.Id,
                status => PublishProjectStatusAsync(session, status),
                logEvent => PublishLogAsync(session, logEvent),
                session.Cancellation.Token);

            await CompleteSessionAsync(session, result);
        }
        catch (OperationCanceledException)
        {
            await CancelSessionAsync(session);
        }
        catch (Exception exception)
        {
            await FailSessionAsync(session, exception);
        }
        finally
        {
            contextGate.Release();
        }
    }

    private async Task ExecuteBootstrapAsync(BuildSession session, SemaphoreSlim contextGate)
    {
        try
        {
            await BeginSessionAsync(session);

            var result = await buildRunner.RunFullBuildAsync(
                session.Context,
                session.Id,
                status => PublishProjectStatusAsync(session, status),
                logEvent => PublishLogAsync(session, logEvent),
                session.Cancellation.Token);

            await CompleteSessionAsync(session, result);
        }
        catch (OperationCanceledException)
        {
            await CancelSessionAsync(session);
        }
        catch (Exception exception)
        {
            await FailSessionAsync(session, exception);
        }
        finally
        {
            contextGate.Release();
        }
    }

    private async Task BeginSessionAsync(BuildSession session)
    {
        session.State = BuildExecutionState.Running;
        session.StartedAt = DateTimeOffset.UtcNow;
        logger.LogInformation("Build {BuildId} is running.", session.Id);
        await PublishBuildStateAsync(session, "BuildStarted");
    }

    private async Task CompleteSessionAsync(BuildSession session, BuildRunResult result)
    {
        session.State = result.Succeeded ? BuildExecutionState.Succeeded : BuildExecutionState.Failed;
        session.ErrorMessage = result.ErrorMessage;
        session.CompletedAt = DateTimeOffset.UtcNow;

        if (result.Succeeded)
        {
            logger.LogInformation("Build {BuildId} succeeded.", session.Id);
        }
        else
        {
            logger.LogError(
                "Build {BuildId} failed with exit code {ExitCode}: {ErrorMessage}",
                session.Id,
                result.ExitCode,
                result.ErrorMessage);
        }

        await PublishBuildStateAsync(session, result.Succeeded ? "BuildCompleted" : "BuildFailed");
    }

    private async Task CancelSessionAsync(BuildSession session)
    {
        var cancelledAt = DateTimeOffset.UtcNow;

        foreach (var project in session.Projects.Values)
        {
            if (project.State is ProjectExecutionState.Building or ProjectExecutionState.Queued)
            {
                project.State = ProjectExecutionState.Cancelled;
                project.Message = "Build was cancelled.";
                project.CompletedAt = cancelledAt;

                await hubContext.Clients
                    .Group(BuildHub.GetBuildGroup(session.Id))
                    .SendAsync("ProjectStatusChanged", new ProjectBuildStatusChangedEvent
                    {
                        BuildId = session.Id,
                        ProjectPath = project.ProjectPath,
                        ProjectName = project.ProjectName,
                        State = project.State,
                        Message = project.Message
                    });
            }
        }

        session.State = BuildExecutionState.Cancelled;
        session.ErrorMessage = "Build was cancelled.";
        session.CompletedAt = DateTimeOffset.UtcNow;
        logger.LogWarning("Build {BuildId} was cancelled.", session.Id);
        await PublishBuildStateAsync(session, "BuildCancelled");
    }

    private async Task FailSessionAsync(BuildSession session, Exception exception)
    {
        logger.LogError(exception, "Build session {BuildId} failed.", session.Id);
        session.State = BuildExecutionState.Failed;
        session.ErrorMessage = exception.Message;
        session.CompletedAt = DateTimeOffset.UtcNow;
        await PublishBuildStateAsync(session, "BuildFailed");
    }

    private static bool IsTerminalProjectState(ProjectExecutionState state)
    {
        return state is ProjectExecutionState.Succeeded or
            ProjectExecutionState.Failed or
            ProjectExecutionState.Skipped or
            ProjectExecutionState.Cancelled;
    }

    private async Task PublishProjectStatusAsync(
        BuildSession session,
        ProjectBuildStatusChangedEvent statusEvent)
    {
        session.Projects.AddOrUpdate(
            statusEvent.ProjectPath,
            _ => new ProjectBuildStatusSnapshot
            {
                ProjectPath = statusEvent.ProjectPath,
                ProjectName = statusEvent.ProjectName,
                State = statusEvent.State,
                Message = statusEvent.Message,
                StartedAt = statusEvent.State == ProjectExecutionState.Building ? statusEvent.Timestamp : null,
                CompletedAt = IsTerminalProjectState(statusEvent.State) ? statusEvent.Timestamp : null
            },
            (_, existing) =>
            {
                existing.State = statusEvent.State;
                existing.Message = statusEvent.Message;

                if (statusEvent.State == ProjectExecutionState.Building)
                {
                    existing.StartedAt ??= statusEvent.Timestamp;
                    existing.CompletedAt = null;
                }

                if (IsTerminalProjectState(statusEvent.State))
                {
                    existing.CompletedAt ??= statusEvent.Timestamp;
                }

                return existing;
            });

        await hubContext.Clients
            .Group(BuildHub.GetBuildGroup(session.Id))
            .SendAsync("ProjectStatusChanged", statusEvent);
    }

    private async Task PublishLogAsync(BuildSession session, BuildLogEvent logEvent)
    {
        session.AddRecentLog(logEvent);
        await AppendLogFileAsync(session, logEvent);

        await hubContext.Clients
            .Group(BuildHub.GetBuildGroup(session.Id))
            .SendAsync("ProjectLog", logEvent);
    }

    private async Task AppendLogFileAsync(BuildSession session, BuildLogEvent logEvent)
    {
        if (string.IsNullOrWhiteSpace(session.LogFilePath))
        {
            return;
        }

        var project = logEvent.ProjectName ?? "build";
        var line = $"[{logEvent.Timestamp:O}] [{logEvent.Stream}] [{project}] {logEvent.Message}{Environment.NewLine}";

        await session.LogWriteLock.WaitAsync();

        try
        {
            await File.AppendAllTextAsync(session.LogFilePath, line, session.Cancellation.Token);
        }
        catch (OperationCanceledException)
        {
            await File.AppendAllTextAsync(session.LogFilePath, line, CancellationToken.None);
        }
        catch (Exception exception)
        {
            logger.LogWarning(exception, "Could not append to build log file {LogFilePath}.", session.LogFilePath);
        }
        finally
        {
            session.LogWriteLock.Release();
        }
    }

    private Task PublishBuildStateAsync(BuildSession session, string eventName)
    {
        return hubContext.Clients
            .Group(BuildHub.GetBuildGroup(session.Id))
            .SendAsync(eventName, session.CreateSnapshot());
    }

    private sealed class CachedProjectGraph(PartialProjectGraph graph, DateTimeOffset loadedAt)
    {
        public PartialProjectGraph Graph { get; } = graph;

        public DateTimeOffset LoadedAt { get; } = loadedAt;
    }

    private sealed class SignalRGraphLoadProgress(
        string progressId,
        IHubContext<BuildHub> hub) : IProgress<ProjectGraphLoadProgress>
    {
        public void Report(ProjectGraphLoadProgress progressEvent)
        {
            hub.Clients
                .Group(BuildHub.GetGraphLoadGroup(progressId))
                .SendAsync("GraphLoadProgress", progressEvent)
                .GetAwaiter()
                .GetResult();
        }
    }
}
