# Low-memory FreshRSS + RSS-Bridge deployment toolkit

This repository contains Bash tooling and configuration templates for deploying **FreshRSS** and **RSS-Bridge** on a dedicated Ubuntu server with only about **100 MiB of RAM available for the added workload**.

It does not use Docker, a database server, Redis, Node.js, compilation, or an application build step.

## What the installer manages

- One nginx worker serving separate FreshRSS and RSS-Bridge virtual hosts.
- One PHP-FPM pool, using `ondemand` mode and at most one worker.
- FreshRSS with SQLite and persistent data in `/var/lib/freshrss/data`.
- RSS-Bridge with persistent cache in `/var/cache/rss-bridge`.
- RSS-Bridge configuration in `/etc/rss-deploy/rss-bridge/config.ini.php`.
- A FreshRSS refresh every 20 minutes through a systemd timer.
- A shared `rss.slice` cgroup containing nginx, PHP-FPM, and refresh jobs.

Application releases are pinned and stored under `/var/lib/rss-deploy/releases`. `/var/www/freshrss` and `/var/www/rss-bridge` are symlinks to the selected releases, so rerunning the installer with a new version does not delete application data.

## Memory expectations

The runtime slice is configured with:

- `MemoryMax=96M`, keeping the managed RSS stack strictly below 100 MiB with a small margin.
- `MemorySwapMax=0`, preventing the stack from hiding excessive demand in swap.
- A 64 MiB per-process PHP memory limit. This is a limit, not a preallocation.

The 96 MiB cgroup covers the long-lived web stack and scheduled refresh processes. It does **not** measure or limit the existing Ubuntu OS, SSH, systemd, or unrelated host services.

A hard memory ceiling is not a promise that every workload will fit. Too many feeds, large feed documents, expensive bridges, or concurrent requests can cause PHP errors or OOM kills. Start with a small feed set and inspect `deploy.sh status` before adding workload.

Package installation runs outside that runtime slice. The installer reduces installation-time memory pressure by using `--no-install-recommends`, performing operations sequentially, and temporarily preventing package-triggered service starts.

## Supported host

Use a dedicated host with:

- A supported Ubuntu LTS release.
- systemd running as PID 1 and cgroup v2 with the memory controller available.
- At least 64 MiB currently reported as `MemAvailable`; approximately 100 MiB is the intended target.
- Root access through `sudo`.
- Two DNS hostnames pointing to the server.
- No pre-existing nginx or PHP-FPM installation that must be preserved.

The first installation deliberately refuses existing nginx/PHP-FPM configuration, existing application paths, or existing FreshRSS data. This prevents the toolkit from taking over an unrelated server. On a managed rerun, the installed applications and timer are stopped before files or packages change.

## Configure

```sh
cp config.env.example config.env
```

Edit `config.env` and set:

- `FRESHRSS_HOST` and `RSS_BRIDGE_HOST` to different hostnames.
- `FRESHRSS_VERSION` and `RSS_BRIDGE_VERSION` to exact stable release tags. Moving refs such as `latest`, `main`, and `master` are rejected.
- Optionally, `FRESHRSS_SHA256` and `RSS_BRIDGE_SHA256` for the corresponding GitHub source archives. If omitted, installation relies on HTTPS and prints a warning.

The configuration file is sourced by Bash and the installer runs as root. Treat it as trusted administrator input; do not use a configuration supplied by an untrusted user.

## Install

```sh
sudo ./deploy.sh --config /path/to/config.env install
```

If `--config` is omitted, the installer looks for `config.env` beside `deploy.sh`, then `/etc/rss-deploy/config.env`.

The installer performs these stages in order:

1. Validate Ubuntu, systemd, cgroup v2, memory availability, configuration, and ownership of the target host.
2. Stop an existing managed workload on reruns.
3. Install packages without recommendations and with service auto-start temporarily disabled.
4. Download and extract one pinned application release at a time, verifying a SHA-256 when supplied.
5. Attach persistent data, cache, and configuration to the selected releases.
6. Render nginx, PHP-FPM, and systemd configuration; back up replaced configuration under `/var/lib/rss-deploy/backups`.
7. Validate the generated nginx/PHP-FPM configuration, then start services and the refresh timer.

If installation fails, the temporary `policy-rc.d` change and download directory are cleaned up. On an upgrade failure, the previous workload may remain stopped; correct the error and rerun the installer.

## Initial application setup

The generated virtual hosts initially listen on plain HTTP. **Configure HTTPS before entering FreshRSS credentials on an untrusted network.** TLS certificate issuance is intentionally not automated: certificate tools can create a temporary memory spike on a host this small. Use an existing TLS proxy, externally provisioned certificates, or a carefully staged certificate installation while the RSS workload is stopped.

Then:

1. Open `http://FRESHRSS_HOST/` and complete FreshRSS setup using **SQLite**.
2. Create the initial user and keep extensions disabled unless their memory cost is understood.
3. Set bounded article retention and avoid importing a large historical archive initially.
4. Open `http://RSS_BRIDGE_HOST/` and enable only the bridges you need.
5. Add RSS-Bridge feed URLs to FreshRSS.

The refresh service has a `ConditionPathExists` for `/var/lib/freshrss/data/config.php`. Until FreshRSS setup creates that file, timer-triggered refreshes are skipped. Once configuration exists, the timer runs every 20 minutes with a small randomized delay. `flock` and systemd's single service instance prevent overlapping actualization processes.

RSS-Bridge can generate expensive responses. Do not expose unrestricted bridge generation to the public Internet; use host firewall/reverse-proxy access controls or HTTP authentication where appropriate.

## Inspect memory and service state

```sh
./deploy.sh status
```

This command is read-only and reports:

- Host `MemAvailable`.
- nginx, PHP-FPM, refresh service, timer, and slice states.
- `rss.slice` current memory, historical peak, configured maximum, swap maximum, and OOM-kill count.

`freshrss-actualize.service` being `inactive` between refresh runs is normal because it is a oneshot service. A non-zero OOM-kill count or a peak at the cgroup maximum means the workload is not operating comfortably within the budget. Reduce feed volume/concurrency or deliberately raise the budget; do not treat the hard cap alone as proof of reliability.

## Upgrade or rerun

1. Set new exact release versions and optional checksums in the same configuration file.
2. Rerun `sudo ./deploy.sh --config /path/to/config.env install`.

The rerun stops the managed workload, disables package-triggered service starts, installs or reuses pinned releases, preserves FreshRSS data and RSS-Bridge cache/configuration, and switches the `/var/www` symlinks. Existing extracted releases remain under `/var/lib/rss-deploy/releases` for manual recovery; remove only old, unused release directories after confirming the active symlinks.

Configuration backups are not application-data backups.

## Backup

Back up at least:

- `/var/lib/freshrss/data` — FreshRSS SQLite database, users, settings, and related data.
- `/etc/rss-deploy/rss-bridge/config.ini.php` — customized RSS-Bridge settings.
- The trusted `config.env` used for deployment.

Use a consistent SQLite backup method or stop writes while copying the data directory. RSS-Bridge's cache is disposable and does not need a full backup.

## Managed paths

| Purpose | Path |
|---|---|
| Toolkit state and pinned releases | `/var/lib/rss-deploy` |
| FreshRSS current release link | `/var/www/freshrss` |
| RSS-Bridge current release link | `/var/www/rss-bridge` |
| FreshRSS persistent data | `/var/lib/freshrss/data` |
| RSS-Bridge persistent cache | `/var/cache/rss-bridge` |
| RSS-Bridge persistent configuration | `/etc/rss-deploy/rss-bridge/config.ini.php` |
| nginx sites | `/etc/nginx/sites-available/{freshrss,rss-bridge}.conf` |
| PHP-FPM pool | `/etc/php/<version>/fpm/pool.d/rss.conf` |
| Memory slice | `/etc/systemd/system/rss.slice` |
| Refresh units | `/etc/systemd/system/freshrss-actualize.{service,timer}` |
| Replaced-configuration backups | `/var/lib/rss-deploy/backups` |
