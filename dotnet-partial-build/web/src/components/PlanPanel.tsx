import type { BuildPlan, BuildSessionSnapshot } from '../types';

interface PlanPanelProps {
  plan: BuildPlan | null;
  validationErrors: string[];
  outputsValidated: boolean;
  planning: boolean;
  starting: boolean;
  session: BuildSessionSnapshot | null;
  onValidatePlan: () => void;
  onStartBuild: () => void;
  onBootstrap: () => void;
  onCancel: () => void;
}

function isBuildActive(session: BuildSessionSnapshot | null): boolean {
  return session?.state === 'Pending' || session?.state === 'Running';
}

export function PlanPanel({
  plan,
  validationErrors,
  outputsValidated,
  planning,
  starting,
  session,
  onValidatePlan,
  onStartBuild,
  onBootstrap,
  onCancel
}: PlanPanelProps) {
  const active = isBuildActive(session);
  const controlsDisabled = active || planning || starting;
  const hasValidationErrors = validationErrors.length > 0;
  const bootstrapSucceeded = Boolean(session?.isBootstrap && session.state === 'Succeeded');
  const bootstrapCurrent = hasValidationErrors && !bootstrapSucceeded;
  const validateCurrent = hasValidationErrors && bootstrapSucceeded;
  const startCurrent = Boolean(plan && !hasValidationErrors);

  let guidance = 'Load a project graph and select projects to rebuild.';

  if (planning) {
    guidance = 'Wait while the build plan is calculated.';
  }
  else if (starting) {
    guidance = 'Wait while the build session is created and reused outputs are validated.';
  }
  else if (active) {
    guidance = 'A build is currently running. Watch the status and logs below.';
  }
  else if (bootstrapCurrent) {
    guidance = 'Missing reusable outputs. Step 1: run a full bootstrap build once. Bootstrap always restores before building.';
  }
  else if (validateCurrent) {
    guidance = 'Bootstrap completed. Step 2: validate the outputs before starting a partial build.';
  }
  else if (startCurrent) {
    guidance = outputsValidated
      ? 'Outputs are validated. Step 3: start the partial build.'
      : 'Plan is ready. You can start directly; validation is optional because Start also validates.';
  }

  return (
    <section className="panel plan-panel">
      <div className="panel-header compact">
        <div>
          <h2>Build plan</h2>
          <p>
            {starting
              ? 'Starting build and validating reused outputs…'
              : planning
                ? 'Calculating build plan…'
                : plan
                  ? `${plan.projects.length} rebuild · ${plan.reusedProjects.length} reuse`
                  : 'Select projects to calculate a plan.'}
          </p>
        </div>
        {session && <span className={`status-badge ${session.state.toLowerCase()}`}>{session.state}</span>}
      </div>

      <div className="workflow-guide">
        <div className="workflow-steps" aria-label="Build workflow">
          <span className={`workflow-step ${bootstrapSucceeded ? 'done' : bootstrapCurrent ? 'current' : ''}`}>
            1 Full bootstrap
          </span>
          <span className={`workflow-step ${outputsValidated && !hasValidationErrors ? 'done' : validateCurrent ? 'current' : ''}`}>
            2 Validate outputs
          </span>
          <span className={`workflow-step ${active ? 'done' : startCurrent ? 'current' : ''}`}>
            3 Start partial build
          </span>
        </div>
        <p>{guidance}</p>
      </div>

      <div className="plan-actions">
        <button
          className={bootstrapCurrent ? 'primary' : undefined}
          disabled={controlsDisabled || !plan}
          onClick={onBootstrap}>
          1 Full bootstrap
        </button>
        <button
          className={validateCurrent ? 'primary' : undefined}
          disabled={controlsDisabled || !plan}
          onClick={onValidatePlan}>
          {outputsValidated && !hasValidationErrors ? '2 ✓ Validated' : hasValidationErrors ? '2 Validate outputs' : '2 Validate outputs (optional)'}
        </button>
        <button
          className={startCurrent ? 'primary' : undefined}
          disabled={controlsDisabled || hasValidationErrors || !plan || plan.projects.length === 0}
          onClick={onStartBuild}>
          {starting ? '3 Starting…' : '3 Start partial build'}
        </button>
        <button className="danger" disabled={!active} onClick={onCancel}>
          Cancel
        </button>
      </div>

      {(planning || starting) && (
        <div className="plan-loading" aria-live="polite">
          <span className="spinner" />
          <div>
            <strong>{starting ? 'Starting build…' : 'Calculating build plan…'}</strong>
            <small>
              {starting
                ? 'The backend is validating reused outputs and creating the build session.'
                : 'Inputs are temporarily locked to prevent changes while the plan is being prepared.'}
            </small>
          </div>
        </div>
      )}

      {validationErrors.length > 0 && (
        <div className="error-box validation-errors">
          <strong>Reused-output validation failed ({validationErrors.length})</strong>
          <p>
            Restore only prepares NuGet assets. These projects must be compiled once before they can be reused.
          </p>
          <div className="error-actions">
            <button
              className={bootstrapCurrent ? 'primary' : undefined}
              disabled={controlsDisabled}
              onClick={onBootstrap}>
              1 Run full bootstrap
            </button>
            <button
              className={validateCurrent ? 'primary' : undefined}
              disabled={controlsDisabled}
              onClick={onValidatePlan}>
              2 Validate outputs
            </button>
          </div>
          <ul>
            {validationErrors.map(error => <li key={error}>{error}</li>)}
          </ul>
        </div>
      )}

      {session?.errorMessage && (
        <div className="error-box">
          <strong>Build message</strong>
          <p>{session.errorMessage}</p>
        </div>
      )}

      <div className="plan-list">
        {plan?.projects.map(project => (
          <div className="plan-item" key={project.projectPath}>
            <span className="plan-order">{project.order}</span>
            <div>
              <strong>{project.projectName}</strong>
              <small>{project.reason}</small>
              <small>{project.relativePath}</small>
            </div>
            <span className={`status-badge ${project.isRoot ? 'root' : project.isExplicitlySelected ? 'rebuild' : 'required'}`}>
              {project.isRoot ? 'Root' : project.isExplicitlySelected ? 'Selected' : 'Required'}
            </span>
          </div>
        ))}
      </div>

      {session?.logFilePath && (
        <div className="log-file">
          Complete log: <code>{session.logFilePath}</code>
        </div>
      )}
    </section>
  );
}
