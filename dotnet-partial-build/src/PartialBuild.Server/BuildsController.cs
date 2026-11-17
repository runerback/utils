using Microsoft.AspNetCore.Mvc;

namespace PartialBuild.Server;

[ApiController]
[Route("api/builds")]
public sealed class BuildsController(
    BuildSessionManager buildSessionManager,
    ILogger<BuildsController> logger) : ControllerBase
{
    private readonly BuildSessionManager buildSessionManager = buildSessionManager;
    private readonly ILogger<BuildsController> logger = logger;

    [HttpPost]
    public async Task<ActionResult<BuildSessionSnapshot>> Start(StartBuildRequest request)
    {
        try
        {
            var session = await buildSessionManager.StartBuildAsync(request);
            return Accepted($"/api/builds/{session.Id}", session);
        }
        catch (BuildValidationException exception)
        {
            logger.LogWarning(
                "Partial build request failed validation:{NewLine}{Errors}",
                Environment.NewLine,
                string.Join(Environment.NewLine, exception.Errors));

            return BadRequest(new
            {
                error = exception.Message,
                validationErrors = exception.Errors
            });
        }
        catch (Exception exception)
        {
            logger.LogError(exception, "Could not start partial build.");
            return BadRequest(new { error = exception.Message });
        }
    }

    [HttpPost("bootstrap")]
    public async Task<ActionResult<BuildSessionSnapshot>> Bootstrap(BootstrapBuildRequest request)
    {
        try
        {
            var session = await buildSessionManager.StartBootstrapAsync(request);
            return Accepted($"/api/builds/{session.Id}", session);
        }
        catch (Exception exception)
        {
            logger.LogError(exception, "Could not start full bootstrap build.");
            return BadRequest(new { error = exception.Message });
        }
    }

    [HttpGet("{buildId}")]
    public ActionResult<BuildSessionSnapshot> Get(string buildId)
    {
        var session = buildSessionManager.GetSession(buildId);
        return session is null ? NotFound() : Ok(session);
    }

    [HttpGet("{buildId}/log")]
    public ActionResult DownloadLog(string buildId)
    {
        var logFilePath = buildSessionManager.GetLogFilePath(buildId);

        if (string.IsNullOrWhiteSpace(logFilePath) || !System.IO.File.Exists(logFilePath))
        {
            return NotFound(new { error = "The build log file does not exist." });
        }

        return PhysicalFile(logFilePath, "text/plain", Path.GetFileName(logFilePath));
    }

    [HttpPost("{buildId}/cancel")]
    public ActionResult Cancel(string buildId)
    {
        return buildSessionManager.Cancel(buildId)
            ? Accepted()
            : Conflict(new { error = "The build does not exist or is already finished." });
    }
}
