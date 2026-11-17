using Microsoft.AspNetCore.Mvc;

namespace PartialBuild.Server;

[ApiController]
[Route("api/graph")]
public sealed class GraphController(
    BuildSessionManager buildSessionManager,
    ILogger<GraphController> logger) : ControllerBase
{
    private readonly BuildSessionManager buildSessionManager = buildSessionManager;
    private readonly ILogger<GraphController> logger = logger;

    [HttpPost("load")]
    public async Task<ActionResult<GraphResponse>> Load(GraphLoadRequest request)
    {
        try
        {
            var graph = await buildSessionManager.LoadGraphAsync(request.Context, request.ProgressId);
            var response = new GraphResponse
            {
                RootProjectPath = graph.RootProjectPath,
                Projects = [.. graph.Nodes.Values
                    .OrderBy(node => node.RelativePath, StringComparer.OrdinalIgnoreCase)
                    .Select(node => new GraphProjectResponse
                    {
                        FullPath = node.FullPath,
                        Name = node.Name,
                        RelativePath = node.RelativePath,
                        Dependencies = [.. node.Dependencies],
                        Dependents = [.. node.Dependents]
                    })]
            };

            return Ok(response);
        }
        catch (Exception exception)
        {
            logger.LogError(exception, "Graph load request failed for {RootProjectPath}.", request.Context.RootProjectPath);
            return BadRequest(new { error = exception.Message });
        }
    }
}
