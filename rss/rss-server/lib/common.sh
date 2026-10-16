#!/usr/bin/env bash

set -Eeuo pipefail

readonly RSS_DEPLOY_STATE_DIR="/var/lib/rss-deploy"
readonly RSS_DEPLOY_MANAGED_FILE="${RSS_DEPLOY_STATE_DIR}/managed"
readonly RSS_DEPLOY_PHP_VERSION_FILE="${RSS_DEPLOY_STATE_DIR}/php-version"
readonly RSS_DEPLOY_RELEASES_DIR="${RSS_DEPLOY_STATE_DIR}/releases"
readonly RSS_DEPLOY_BACKUPS_DIR="${RSS_DEPLOY_STATE_DIR}/backups"
readonly FRESHRSS_ROOT="/var/www/freshrss"
readonly RSS_BRIDGE_ROOT="/var/www/rss-bridge"
readonly FRESHRSS_DATA_DIR="/var/lib/freshrss/data"
readonly RSS_BRIDGE_CACHE_DIR="/var/cache/rss-bridge"
readonly RSS_BRIDGE_CONFIG_DIR="/etc/rss-deploy/rss-bridge"
readonly RSS_BRIDGE_CONFIG_FILE="${RSS_BRIDGE_CONFIG_DIR}/config.ini.php"
readonly PHP_FPM_SOCKET="/run/php/rss-fpm.sock"
readonly RSS_CGROUP_DIR="/sys/fs/cgroup/rss.slice"

BACKUP_DIR=""

log() {
    printf '[rss-deploy] %s\n' "$*"
}

warn() {
    printf '[rss-deploy] WARNING: %s\n' "$*" >&2
}

die() {
    printf '[rss-deploy] ERROR: %s\n' "$*" >&2
    exit 1
}

require_root() {
    (( EUID == 0 )) || die "The install command must run as root. Use sudo."
}

mem_available_kib() {
    local value
    value=$(awk '/^MemAvailable:/ { print $2; exit }' /proc/meminfo 2>/dev/null || true)
    [[ "$value" =~ ^[0-9]+$ ]] || return 1
    printf '%s\n' "$value"
}

format_bytes() {
    local value="${1:-}"
    if [[ ! "$value" =~ ^[0-9]+$ ]]; then
        printf '%s' "${value:-unavailable}"
    elif command -v numfmt >/dev/null 2>&1; then
        numfmt --to=iec-i --suffix=B "$value"
    else
        printf '%s bytes' "$value"
    fi
}

backup_file() {
    local source="$1"
    [[ -e "$source" || -L "$source" ]] || return 0

    if [[ -z "$BACKUP_DIR" ]]; then
        install -d -m 0700 "$RSS_DEPLOY_BACKUPS_DIR"
        BACKUP_DIR=$(mktemp -d "${RSS_DEPLOY_BACKUPS_DIR}/run.XXXXXX")
    fi

    local relative="${source#/}"
    install -d -m 0700 "$(dirname "${BACKUP_DIR}/${relative}")"
    cp -a -- "$source" "${BACKUP_DIR}/${relative}"
}

install_static() {
    local source="$1"
    local destination="$2"
    local mode="${3:-0644}"

    [[ -f "$source" ]] || die "Template not found: $source"
    backup_file "$destination"
    install -d -m 0755 "$(dirname "$destination")"
    [[ ! -L "$destination" ]] || rm -f -- "$destination"
    install -m "$mode" "$source" "$destination"
}

render_template() {
    local source="$1"
    local destination="$2"
    local variables="$3"
    local temporary

    [[ -f "$source" ]] || die "Template not found: $source"
    command -v envsubst >/dev/null 2>&1 || die "envsubst is required (package: gettext-base)."
    backup_file "$destination"
    install -d -m 0755 "$(dirname "$destination")"
    [[ ! -L "$destination" ]] || rm -f -- "$destination"

    temporary=$(mktemp "${destination}.tmp.XXXXXX")
    if ! envsubst "$variables" < "$source" > "$temporary"; then
        rm -f -- "$temporary"
        die "Could not render template: $source"
    fi
    chmod 0644 "$temporary"
    mv -f -- "$temporary" "$destination"
}

replace_symlink() {
    local target="$1"
    local link="$2"

    if [[ -e "$link" && ! -L "$link" ]]; then
        die "Refusing to replace non-symlink path: $link"
    fi
    install -d -m 0755 "$(dirname "$link")"
    ln -sfn -- "$target" "$link"
}

unit_state() {
    local unit="$1"
    if ! command -v systemctl >/dev/null 2>&1; then
        printf 'unavailable (systemctl not found)'
        return 0
    fi
    systemctl is-active "$unit" 2>/dev/null || true
}
