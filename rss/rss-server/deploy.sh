#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

COMMAND=""
CONFIG_PATH=""
WORK_DIR=""
PHP_VERSION=""
FRESHRSS_RELEASE_DIR=""
RSS_BRIDGE_RELEASE_DIR=""
POLICY_RC_D_ACTIVE=0
POLICY_RC_D_EXISTED=0

usage() {
    cat <<'EOF'
Usage:
  ./deploy.sh --config /path/to/config.env install
  ./deploy.sh status

Commands:
  install   Install or rerun the managed deployment (requires root).
  status    Show service state and memory usage without changing the host.

Options:
  -c, --config PATH   Configuration file for install.
  -h, --help          Show this help.
EOF
}

restore_policy_rc_d() {
    (( POLICY_RC_D_ACTIVE == 1 )) || return 0

    rm -f -- /usr/sbin/policy-rc.d
    if (( POLICY_RC_D_EXISTED == 1 )); then
        cp -a -- "${WORK_DIR}/policy-rc.d.original" /usr/sbin/policy-rc.d
    fi
    POLICY_RC_D_ACTIVE=0
}

cleanup() {
    local exit_code=$?
    restore_policy_rc_d || true
    if [[ -n "$WORK_DIR" && -d "$WORK_DIR" && "$WORK_DIR" == "${RSS_DEPLOY_STATE_DIR}"/work.* ]]; then
        rm -rf -- "$WORK_DIR"
    fi
    return "$exit_code"
}
trap cleanup EXIT

parse_arguments() {
    while (( $# > 0 )); do
        case "$1" in
            -c|--config)
                (( $# >= 2 )) || die "$1 requires a path."
                CONFIG_PATH="$2"
                shift 2
                ;;
            install|status)
                [[ -z "$COMMAND" ]] || die "Specify exactly one command."
                COMMAND="$1"
                shift
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            *)
                usage >&2
                die "Unknown argument: $1"
                ;;
        esac
    done

    [[ -n "$COMMAND" ]] || {
        usage >&2
        exit 2
    }
}

load_and_validate_config() {
    if [[ -z "$CONFIG_PATH" ]]; then
        if [[ -f "${SCRIPT_DIR}/config.env" ]]; then
            CONFIG_PATH="${SCRIPT_DIR}/config.env"
        elif [[ -f /etc/rss-deploy/config.env ]]; then
            CONFIG_PATH=/etc/rss-deploy/config.env
        else
            CONFIG_PATH="${SCRIPT_DIR}/config.env"
        fi
    fi

    [[ -f "$CONFIG_PATH" ]] || die "Configuration not found: $CONFIG_PATH. Copy config.env.example first."
    # The configuration is explicitly supplied by the administrator running this root-level installer.
    # shellcheck disable=SC1090
    source "$CONFIG_PATH"

    local name
    for name in FRESHRSS_HOST RSS_BRIDGE_HOST FRESHRSS_VERSION RSS_BRIDGE_VERSION; do
        [[ -n "${!name:-}" ]] || die "$name must be set in $CONFIG_PATH."
    done

    FRESHRSS_SHA256="${FRESHRSS_SHA256:-}"
    RSS_BRIDGE_SHA256="${RSS_BRIDGE_SHA256:-}"

    local host
    for host in "$FRESHRSS_HOST" "$RSS_BRIDGE_HOST"; do
        [[ "$host" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$ ]] || die "Invalid hostname: $host"
        [[ "$host" != *..* ]] || die "Invalid hostname: $host"
        (( ${#host} <= 253 )) || die "Hostname is too long: $host"
    done
    [[ "$FRESHRSS_HOST" != "$RSS_BRIDGE_HOST" ]] || die "FreshRSS and RSS-Bridge need different hostnames."

    local version
    for version in "$FRESHRSS_VERSION" "$RSS_BRIDGE_VERSION"; do
        [[ "$version" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || die "Invalid or unsafe release tag: $version"
        [[ "$version" != latest && "$version" != master && "$version" != main ]] || die "Use a pinned stable release tag, not $version."
    done

    local checksum
    for checksum in "$FRESHRSS_SHA256" "$RSS_BRIDGE_SHA256"; do
        [[ -z "$checksum" || "$checksum" =~ ^[A-Fa-f0-9]{64}$ ]] || die "SHA-256 values must contain exactly 64 hexadecimal characters."
    done

    export FRESHRSS_HOST RSS_BRIDGE_HOST FRESHRSS_ROOT RSS_BRIDGE_ROOT PHP_FPM_SOCKET
    FRESHRSS_RELEASE_DIR="${RSS_DEPLOY_RELEASES_DIR}/freshrss-${FRESHRSS_VERSION}"
    RSS_BRIDGE_RELEASE_DIR="${RSS_DEPLOY_RELEASES_DIR}/rss-bridge-${RSS_BRIDGE_VERSION}"
}

preflight_install() {
    require_root
    [[ -r /etc/os-release ]] || die "Cannot identify this operating system."
    local os_id
    os_id=$(source /etc/os-release; printf '%s' "${ID:-}")
    [[ "$os_id" == ubuntu ]] || die "This toolkit supports Ubuntu only."
    [[ -d /run/systemd/system ]] || die "systemd is not running."
    command -v apt-get >/dev/null 2>&1 || die "apt-get is required."
    command -v systemctl >/dev/null 2>&1 || die "systemctl is required."
    [[ -f /sys/fs/cgroup/cgroup.controllers ]] || die "cgroup v2 is required for the shared memory limit."
    grep -qw memory /sys/fs/cgroup/cgroup.controllers || die "The cgroup v2 memory controller is unavailable."

    local available
    available=$(mem_available_kib) || die "Cannot read MemAvailable from /proc/meminfo."
    if (( available < 65536 )); then
        die "Less than 64 MiB is currently available. Free memory before installation."
    elif (( available < 102400 )); then
        warn "Less than 100 MiB is currently available; installation may fail if other host workload increases."
    fi

    if [[ ! -f "$RSS_DEPLOY_MANAGED_FILE" ]]; then
        [[ ! -e /etc/nginx && ! -L /etc/nginx ]] || die "Existing nginx configuration found. Use a dedicated host or migrate it manually first."
        [[ ! -e "$FRESHRSS_ROOT" && ! -L "$FRESHRSS_ROOT" ]] || die "Existing path found: $FRESHRSS_ROOT"
        [[ ! -e "$RSS_BRIDGE_ROOT" && ! -L "$RSS_BRIDGE_ROOT" ]] || die "Existing path found: $RSS_BRIDGE_ROOT"
        [[ ! -e "$FRESHRSS_DATA_DIR" && ! -L "$FRESHRSS_DATA_DIR" ]] || die "Existing FreshRSS data found: $FRESHRSS_DATA_DIR"
        ! compgen -G '/etc/php/*/fpm/php-fpm.conf' >/dev/null || die "Existing PHP-FPM configuration found. This installer requires ownership of PHP-FPM."
    fi
}

stop_managed_services() {
    [[ -f "$RSS_DEPLOY_MANAGED_FILE" ]] || return 0

    local unit
    for unit in freshrss-actualize.timer freshrss-actualize.service nginx.service; do
        if systemctl cat "$unit" >/dev/null 2>&1; then
            log "Stopping $unit before changing files"
            systemctl stop "$unit"
        fi
    done

    if [[ -s "$RSS_DEPLOY_PHP_VERSION_FILE" ]]; then
        local old_php_version
        IFS= read -r old_php_version < "$RSS_DEPLOY_PHP_VERSION_FILE"
        if [[ "$old_php_version" =~ ^[0-9]+\.[0-9]+$ ]]; then
            unit="php${old_php_version}-fpm.service"
            if systemctl cat "$unit" >/dev/null 2>&1; then
                log "Stopping $unit before changing files"
                systemctl stop "$unit"
            fi
        fi
    fi
}

prepare_state() {
    install -d -m 0755 "$RSS_DEPLOY_STATE_DIR" "$RSS_DEPLOY_RELEASES_DIR" /var/www
    install -d -m 0700 "$RSS_DEPLOY_BACKUPS_DIR"
    touch "$RSS_DEPLOY_MANAGED_FILE"
    chmod 0644 "$RSS_DEPLOY_MANAGED_FILE"
    WORK_DIR=$(mktemp -d "${RSS_DEPLOY_STATE_DIR}/work.XXXXXX")
}

install_packages() {
    log "Installing the minimal package set sequentially"
    if [[ -e /usr/sbin/policy-rc.d || -L /usr/sbin/policy-rc.d ]]; then
        cp -a -- /usr/sbin/policy-rc.d "${WORK_DIR}/policy-rc.d.original"
        POLICY_RC_D_EXISTED=1
        rm -f -- /usr/sbin/policy-rc.d
    fi
    cat > /usr/sbin/policy-rc.d <<'EOF'
#!/bin/sh
# Temporarily installed by rss-deploy; prevents package-triggered service starts.
exit 101
EOF
    chmod 0755 /usr/sbin/policy-rc.d
    POLICY_RC_D_ACTIVE=1

    DEBIAN_FRONTEND=noninteractive apt-get -o Acquire::Retries=3 update
    DEBIAN_FRONTEND=noninteractive apt-get -o Acquire::Retries=3 install -y --no-install-recommends \
        ca-certificates \
        curl \
        gettext-base \
        gzip \
        nginx \
        php-cli \
        php-curl \
        php-fpm \
        php-gd \
        php-intl \
        php-mbstring \
        php-sqlite3 \
        php-xml \
        php-zip \
        tar \
        util-linux

    restore_policy_rc_d
    getent passwd www-data >/dev/null 2>&1 || die "The www-data user was not created by the web packages."
}

detect_php_version() {
    command -v php >/dev/null 2>&1 || die "PHP CLI installation did not provide php."
    PHP_VERSION=$(php -n -r 'echo PHP_MAJOR_VERSION, ".", PHP_MINOR_VERSION;')
    [[ "$PHP_VERSION" =~ ^[0-9]+\.[0-9]+$ ]] || die "Could not determine the PHP version."
    printf '%s\n' "$PHP_VERSION" > "${RSS_DEPLOY_STATE_DIR}/php-version.tmp"
    mv -f -- "${RSS_DEPLOY_STATE_DIR}/php-version.tmp" "$RSS_DEPLOY_PHP_VERSION_FILE"
}

install_release() {
    local name="$1"
    local version="$2"
    local checksum="$3"
    local release_dir="$4"
    local url="https://github.com/${5}/archive/refs/tags/${version}.tar.gz"
    local archive="${WORK_DIR}/${name}.tar.gz"
    local extract_dir="${WORK_DIR}/${name}.extract"

    if [[ -d "$release_dir" ]]; then
        [[ -f "${release_dir}/.rss-deploy-release" ]] || die "Unrecognized existing release directory: $release_dir"
        log "Reusing pinned $name release $version"
        return 0
    fi

    log "Downloading $name release $version"
    curl --fail --location --proto '=https' --tlsv1.2 \
        --connect-timeout 20 --max-time 600 --retry 3 --retry-delay 2 \
        --output "$archive" "$url"

    if [[ -n "$checksum" ]]; then
        printf '%s  %s\n' "$checksum" "$archive" | sha256sum -c - >/dev/null || die "Checksum verification failed for $name $version."
    else
        warn "No SHA-256 supplied for $name $version; relying on HTTPS and the pinned tag."
    fi

    install -d -m 0700 "$extract_dir"
    tar -xzf "$archive" -C "$extract_dir" --strip-components=1
    rm -f -- "$archive"

    case "$name" in
        freshrss)
            [[ -d "${extract_dir}/p" && -f "${extract_dir}/app/actualize_script.php" ]] || die "The FreshRSS archive has an unexpected layout."
            ;;
        rss-bridge)
            [[ -f "${extract_dir}/index.php" ]] || die "The RSS-Bridge archive has an unexpected layout."
            ;;
    esac

    {
        printf 'name=%s\n' "$name"
        printf 'version=%s\n' "$version"
        printf 'url=%s\n' "$url"
        printf 'sha256=%s\n' "$checksum"
    } > "${extract_dir}/.rss-deploy-release"
    chown -R root:root "$extract_dir"
    chmod -R go-w "$extract_dir"
    mv -- "$extract_dir" "$release_dir"
}

link_into_release() {
    local target="$1"
    local link="$2"

    if [[ -L "$link" || -f "$link" ]]; then
        rm -f -- "$link"
    elif [[ -d "$link" ]]; then
        rm -rf -- "$link"
    elif [[ -e "$link" ]]; then
        die "Unexpected filesystem object: $link"
    fi
    ln -s -- "$target" "$link"
}

prepare_persistent_files() {
    log "Attaching persistent data, cache, and configuration"
    if [[ ! -d "$FRESHRSS_DATA_DIR" ]]; then
        install -d -m 0750 -o www-data -g www-data "$FRESHRSS_DATA_DIR"
        if [[ -d "${FRESHRSS_RELEASE_DIR}/data" && ! -L "${FRESHRSS_RELEASE_DIR}/data" ]]; then
            cp -a -- "${FRESHRSS_RELEASE_DIR}/data/." "$FRESHRSS_DATA_DIR/"
        fi
        chown -R www-data:www-data "$FRESHRSS_DATA_DIR"
    else
        chown www-data:www-data "$FRESHRSS_DATA_DIR"
        chmod 0750 "$FRESHRSS_DATA_DIR"
    fi
    link_into_release "$FRESHRSS_DATA_DIR" "${FRESHRSS_RELEASE_DIR}/data"

    if [[ ! -d "$RSS_BRIDGE_CACHE_DIR" ]]; then
        install -d -m 0750 -o www-data -g www-data "$RSS_BRIDGE_CACHE_DIR"
        if [[ -d "${RSS_BRIDGE_RELEASE_DIR}/cache" && ! -L "${RSS_BRIDGE_RELEASE_DIR}/cache" ]]; then
            cp -a -- "${RSS_BRIDGE_RELEASE_DIR}/cache/." "$RSS_BRIDGE_CACHE_DIR/"
        fi
        chown -R www-data:www-data "$RSS_BRIDGE_CACHE_DIR"
    else
        chown www-data:www-data "$RSS_BRIDGE_CACHE_DIR"
        chmod 0750 "$RSS_BRIDGE_CACHE_DIR"
    fi
    link_into_release "$RSS_BRIDGE_CACHE_DIR" "${RSS_BRIDGE_RELEASE_DIR}/cache"

    install -d -m 0755 /etc/rss-deploy
    install -d -m 0750 -o root -g www-data "$RSS_BRIDGE_CONFIG_DIR"
    if [[ ! -f "$RSS_BRIDGE_CONFIG_FILE" && -f "${RSS_BRIDGE_RELEASE_DIR}/config.default.ini.php" ]]; then
        install -m 0640 -o root -g www-data \
            "${RSS_BRIDGE_RELEASE_DIR}/config.default.ini.php" "$RSS_BRIDGE_CONFIG_FILE"
    fi
    if [[ -f "$RSS_BRIDGE_CONFIG_FILE" ]]; then
        chown root:www-data "$RSS_BRIDGE_CONFIG_FILE"
        chmod 0640 "$RSS_BRIDGE_CONFIG_FILE"
        link_into_release "$RSS_BRIDGE_CONFIG_FILE" "${RSS_BRIDGE_RELEASE_DIR}/config.ini.php"
    else
        warn "RSS-Bridge default configuration was not found; using application built-in defaults."
    fi

    replace_symlink "$FRESHRSS_RELEASE_DIR" "$FRESHRSS_ROOT"
    replace_symlink "$RSS_BRIDGE_RELEASE_DIR" "$RSS_BRIDGE_ROOT"
}

configure_services() {
    log "Rendering nginx, PHP-FPM, and systemd configuration"
    local default_pool="/etc/php/${PHP_VERSION}/fpm/pool.d/www.conf"
    if [[ -f "$default_pool" ]]; then
        backup_file "$default_pool"
        rm -f -- "$default_pool"
    fi

    install_static "${SCRIPT_DIR}/templates/nginx/nginx.conf" /etc/nginx/nginx.conf
    install_static "${SCRIPT_DIR}/templates/php-fpm/rss-pool.conf" "/etc/php/${PHP_VERSION}/fpm/pool.d/rss.conf"
    install_static "${SCRIPT_DIR}/templates/systemd/rss.slice" /etc/systemd/system/rss.slice
    install_static "${SCRIPT_DIR}/templates/systemd/freshrss-actualize.service" /etc/systemd/system/freshrss-actualize.service
    install_static "${SCRIPT_DIR}/templates/systemd/freshrss-actualize.timer" /etc/systemd/system/freshrss-actualize.timer
    install_static "${SCRIPT_DIR}/templates/systemd/rss-service-slice.conf" /etc/systemd/system/nginx.service.d/rss-slice.conf
    install_static "${SCRIPT_DIR}/templates/systemd/rss-service-slice.conf" "/etc/systemd/system/php${PHP_VERSION}-fpm.service.d/rss-slice.conf"

    render_template "${SCRIPT_DIR}/templates/nginx/freshrss.conf" /etc/nginx/sites-available/freshrss.conf \
        '${FRESHRSS_HOST} ${FRESHRSS_ROOT} ${PHP_FPM_SOCKET}'
    render_template "${SCRIPT_DIR}/templates/nginx/rss-bridge.conf" /etc/nginx/sites-available/rss-bridge.conf \
        '${RSS_BRIDGE_HOST} ${RSS_BRIDGE_ROOT} ${PHP_FPM_SOCKET}'

    if [[ -e /etc/nginx/sites-enabled/default || -L /etc/nginx/sites-enabled/default ]]; then
        backup_file /etc/nginx/sites-enabled/default
        rm -f -- /etc/nginx/sites-enabled/default
    fi
    replace_symlink /etc/nginx/sites-available/freshrss.conf /etc/nginx/sites-enabled/freshrss.conf
    replace_symlink /etc/nginx/sites-available/rss-bridge.conf /etc/nginx/sites-enabled/rss-bridge.conf

    systemctl daemon-reload
    command -v "php-fpm${PHP_VERSION}" >/dev/null 2>&1 || die "PHP-FPM binary not found for PHP $PHP_VERSION."
    "php-fpm${PHP_VERSION}" -t
    nginx -t

    systemctl start rss.slice
    systemctl enable "php${PHP_VERSION}-fpm.service" nginx.service
    systemctl restart "php${PHP_VERSION}-fpm.service"
    systemctl restart nginx.service
    systemctl enable freshrss-actualize.timer
    systemctl restart freshrss-actualize.timer
}

read_cgroup_value() {
    local file="$1"
    local value
    [[ -r "$file" ]] || return 1
    IFS= read -r value < "$file" || return 1
    printf '%s\n' "$value"
}

show_status() {
    printf 'Host\n'
    local available
    if available=$(mem_available_kib); then
        printf '  MemAvailable: %s\n' "$(format_bytes "$(( available * 1024 ))")"
    else
        printf '  MemAvailable: unavailable\n'
    fi

    printf '\nServices\n'
    local unit state
    for unit in rss.slice nginx.service freshrss-actualize.service freshrss-actualize.timer; do
        state=$(unit_state "$unit")
        printf '  %-36s %s\n' "$unit" "${state:-unknown}"
    done
    if [[ -s "$RSS_DEPLOY_PHP_VERSION_FILE" ]]; then
        local php_version
        IFS= read -r php_version < "$RSS_DEPLOY_PHP_VERSION_FILE"
        unit="php${php_version}-fpm.service"
        state=$(unit_state "$unit")
        printf '  %-36s %s\n' "$unit" "${state:-unknown}"
    else
        printf '  %-36s %s\n' 'php-fpm.service' 'version not recorded'
    fi

    printf '\nMemory cgroup: rss.slice\n'
    if [[ ! -d "$RSS_CGROUP_DIR" ]]; then
        printf '  cgroup is not present; services may not have started yet.\n'
        return 0
    fi

    local current peak maximum swap_maximum oom_kills
    current=$(read_cgroup_value "${RSS_CGROUP_DIR}/memory.current" || true)
    peak=$(read_cgroup_value "${RSS_CGROUP_DIR}/memory.peak" || true)
    maximum=$(read_cgroup_value "${RSS_CGROUP_DIR}/memory.max" || true)
    swap_maximum=$(read_cgroup_value "${RSS_CGROUP_DIR}/memory.swap.max" || true)
    oom_kills=$(awk '$1 == "oom_kill" { print $2; exit }' "${RSS_CGROUP_DIR}/memory.events" 2>/dev/null || true)

    printf '  Current: %s\n' "$(format_bytes "$current")"
    printf '  Peak:    %s\n' "$(format_bytes "$peak")"
    printf '  Maximum: %s\n' "$(format_bytes "$maximum")"
    printf '  Swap max: %s\n' "$(format_bytes "$swap_maximum")"
    printf '  OOM kills: %s\n' "${oom_kills:-unavailable}"

    if [[ "$oom_kills" =~ ^[0-9]+$ ]] && (( oom_kills > 0 )); then
        warn "The RSS cgroup has recorded OOM kills. Reduce feed volume/concurrency or raise the memory budget."
    elif [[ "$peak" =~ ^[0-9]+$ && "$maximum" =~ ^[0-9]+$ ]] && (( peak >= maximum )); then
        warn "Peak memory reached the cgroup ceiling. Check logs before increasing workload."
    fi
}

run_install() {
    load_and_validate_config
    preflight_install
    stop_managed_services
    prepare_state
    install_packages
    detect_php_version
    install_release freshrss "$FRESHRSS_VERSION" "$FRESHRSS_SHA256" "$FRESHRSS_RELEASE_DIR" FreshRSS/FreshRSS
    install_release rss-bridge "$RSS_BRIDGE_VERSION" "$RSS_BRIDGE_SHA256" "$RSS_BRIDGE_RELEASE_DIR" RSS-Bridge/rss-bridge
    prepare_persistent_files
    configure_services

    log "Installation complete."
    printf '\nFreshRSS:  http://%s/\nRSS-Bridge: http://%s/\n\n' "$FRESHRSS_HOST" "$RSS_BRIDGE_HOST"
    printf 'Complete FreshRSS setup with SQLite before adding feeds. The refresh timer remains skipped until data/config.php exists.\n'
    printf 'Do not submit login credentials over plain HTTP on an untrusted network; configure HTTPS first.\n'
    printf 'Inspect runtime memory with: %s status\n' "$0"
}

parse_arguments "$@"
case "$COMMAND" in
    install)
        run_install
        ;;
    status)
        show_status
        ;;
esac
