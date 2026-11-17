using System.Text.Json.Serialization;
using Microsoft.Build.Locator;
using PartialBuild.Core;
using PartialBuild.MSBuild;
using PartialBuild.Server;

MSBuildLocator.RegisterDefaults();

const string DevelopmentCorsPolicy = "DevelopmentCors";

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers()
    .AddJsonOptions(options =>
    {
        options.JsonSerializerOptions.Converters.Add(new JsonStringEnumConverter());
    });

builder.Services.AddSignalR()
    .AddJsonProtocol(options =>
    {
        options.PayloadSerializerOptions.Converters.Add(new JsonStringEnumConverter());
    });

builder.Services.AddCors(options =>
{
    options.AddPolicy(DevelopmentCorsPolicy, policy =>
    {
        policy
            .WithOrigins(
                "http://localhost:5173",
                "https://localhost:5173",
                "http://127.0.0.1:5173",
                "https://127.0.0.1:5173")
            .AllowAnyHeader()
            .AllowAnyMethod()
            .AllowCredentials();
    });
});

builder.Services.AddSingleton<ProjectGraphLoader>();
builder.Services.AddSingleton<RebuildPlanner>();
builder.Services.AddSingleton<ProjectOutputLocator>();
builder.Services.AddSingleton<OutputValidator>();
builder.Services.AddSingleton<BuildRunner>();
builder.Services.AddSingleton<BuildSessionManager>();

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.UseCors(DevelopmentCorsPolicy);
}

app.UseDefaultFiles();
app.UseStaticFiles();

app.MapControllers();
app.MapHub<BuildHub>("/hubs/builds");
app.MapGet("/api/health", () => Results.Ok(new
{
    status = "ok",
    utcTime = DateTimeOffset.UtcNow
}));

app.MapFallbackToFile("index.html");
app.Run();
