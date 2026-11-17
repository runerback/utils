using Microsoft.AspNetCore.Mvc;

namespace PartialBuild.Server;

[ApiController]
[Route("api/plan")]
public sealed class PlanController(
    BuildSessionManager buildSessionManager,
    ILogger<PlanController> logger) : ControllerBase
{
    private readonly BuildSessionManager buildSessionManager = buildSessionManager;
    private readonly ILogger<PlanController> logger = logger;

    [HttpPost]
    public async Task<ActionResult<BuildPlanResponse>> Create(BuildPlanRequest request)
    {
        try
        {
            var plan = await buildSessionManager.CreatePlanAsync(request);
            var validationErrors = plan.ReusedProjects
                .Where(project => project.ValidationError is not null)
                .Select(project => $"{project.RelativePath}: {project.ValidationError}")
                .ToList();

            return Ok(new BuildPlanResponse
            {
                Plan = plan,
                CanStart = !plan.HasValidationErrors,
                ValidationErrors = validationErrors
            });
        }
        catch (Exception exception)
        {
            logger.LogError(exception, "Could not create build plan for {RootProjectPath}.", request.Context.RootProjectPath);
            return BadRequest(new { error = exception.Message });
        }
    }
}
