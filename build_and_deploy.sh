#!/bin/bash
# build_and_deploy.sh — Build the tower-defense mod and deploy it to configured destinations.
# On first run, asks for deployment paths and saves them to .deploy.conf (gitignored).
# Usage: ./build_and_deploy.sh [--no-server] [--background]
#   --no-server   Skip all server stop/start logic
#   --background  Start the server in the background (nohup) instead of the foreground

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="$SCRIPT_DIR/.deploy.conf"
NO_SERVER=false
BACKGROUND=false

for arg in "$@"; do
  case $arg in
    --no-server)  NO_SERVER=true ;;
    --background) BACKGROUND=true ;;
    *) echo "Unknown argument: $arg"; exit 1 ;;
  esac
done

# ── Colours ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
log()  { echo -e "${GREEN}[deploy]${NC} $*"; }
info() { echo -e "${CYAN}[info]${NC}   $*"; }
warn() { echo -e "${YELLOW}[warn]${NC}   $*"; }
err()  { echo -e "${RED}[error]${NC}  $*" >&2; }
ok()   { echo -e "${GREEN}[deploy]${NC} ${BOLD}✓${NC} $*"; }

# ── Config ─────────────────────────────────────────────────────────────────────
if [[ ! -f "$CONFIG_FILE" ]]; then
  echo ""
  echo "First-time setup — where should the mod be deployed?"
  echo "(Leave blank to skip a destination.)"
  echo ""

  # Only prompt if stdin is a terminal
  if [[ ! -t 0 ]]; then
    err "No .deploy.conf found and stdin is not a terminal. Create $CONFIG_FILE manually:"
    echo "  INSTANCE_MODS=<path or none>"
    echo "  SERVER_DIR=<path or none>"
    exit 1
  fi

  read -rp "  Minecraft instance mods folder (e.g. ~/.minecraft/mods): " _instance
  read -rp "  Minecraft server folder (parent of mods/):                 " _server

  # Expand ~ manually
  _instance="${_instance/#\~/$HOME}"
  _server="${_server/#\~/$HOME}"

  cat > "$CONFIG_FILE" <<EOF
# Deployment configuration — machine-specific, gitignored.
# Edit this file to change paths. Delete it to re-run first-time setup.
INSTANCE_MODS="${_instance:-none}"
SERVER_DIR="${_server:-none}"
EOF
  log "Config saved to $CONFIG_FILE"
  echo ""
fi

# shellcheck source=/dev/null
source "$CONFIG_FILE"
INSTANCE_MODS="${INSTANCE_MODS:-none}"
SERVER_DIR="${SERVER_DIR:-none}"

# ── Helpers ────────────────────────────────────────────────────────────────────
find_mod_jar() {
  ls -t "$SCRIPT_DIR/build/libs"/tower-defense-*.jar 2>/dev/null \
    | grep -v -e '-sources\.jar' -e '-dev\.jar' \
    | head -1
}

find_server_launcher() {
  local dir="$1"
  ls "$dir"/fabric-server-mc.*.jar 2>/dev/null | head -1 \
    || ls "$dir"/fabric-server-launch.jar 2>/dev/null | head -1 \
    || true
}

server_pid() {
  ps aux \
    | grep -E 'java.*(fabric-server-mc|fabric-server-launch).*\.jar' \
    | grep -v grep \
    | awk '{print $2}' \
    | head -1 \
    || true
}

# Copy a JAR to a destination folder and confirm with file size.
deploy_jar() {
  local src="$1"
  local dest_dir="$2"
  local label="$3"
  local jar_name
  jar_name=$(basename "$src")
  local dest="$dest_dir/$jar_name"

  # Windows-mounted paths (/mnt/...) don't support rm from WSL
  if [[ "$dest_dir" == /mnt/* ]]; then
    cp -f "$src" "$dest_dir/"
  else
    rm -f "$dest_dir"/tower-defense-*.jar
    cp "$src" "$dest_dir/"
  fi

  local size
  size=$(du -sh "$dest" 2>/dev/null | cut -f1)
  ok "$label → $dest  (${size})"
}

# ── Step 1: Stop server if running ─────────────────────────────────────────────
SERVER_WAS_RUNNING=false
if [[ "$SERVER_DIR" != "none" && "$NO_SERVER" == false ]]; then
  PID=$(server_pid)
  if [[ -n "$PID" ]]; then
    SERVER_WAS_RUNNING=true
    log "Server is running (PID $PID) — stopping gracefully..."
    kill -TERM "$PID"
    WAITED=0
    while kill -0 "$PID" 2>/dev/null; do
      sleep 1
      (( WAITED++ ))
      printf '\r%s' "  waiting for shutdown... ${WAITED}s"
      if [[ $WAITED -ge 15 ]]; then
        echo ""
        warn "Server did not stop after 15 s — force killing..."
        kill -KILL "$PID" 2>/dev/null || true
        sleep 1
        break
      fi
    done
    echo ""
    ok "Server stopped."
  else
    info "Server is not running."
  fi
fi

# ── Step 2: Build ──────────────────────────────────────────────────────────────
log "Building mod..."
cd "$SCRIPT_DIR"
if ! ./gradlew build; then
  err "Build failed."
  if $SERVER_WAS_RUNNING; then
    warn "The server was stopped but the build failed — start it manually."
  fi
  exit 1
fi
ok "Build successful."

# ── Step 3: Locate JAR ─────────────────────────────────────────────────────────
JAR=$(find_mod_jar)
if [[ -z "$JAR" ]]; then
  err "Built JAR not found in build/libs/. Something went wrong."
  exit 1
fi
JAR_NAME=$(basename "$JAR")
JAR_SIZE=$(du -sh "$JAR" | cut -f1)
ok "JAR ready: $JAR_NAME  (${JAR_SIZE})"

# ── Step 4: Deploy to instance ─────────────────────────────────────────────────
if [[ "$INSTANCE_MODS" != "none" ]]; then
  if [[ ! -d "$INSTANCE_MODS" ]]; then
    warn "Instance mods folder not found: $INSTANCE_MODS (skipping)"
  else
    log "Deploying to Minecraft instance..."
    deploy_jar "$JAR" "$INSTANCE_MODS" "Instance"
  fi
fi

# ── Step 5: Deploy to server ───────────────────────────────────────────────────
if [[ "$SERVER_DIR" != "none" ]]; then
  SERVER_MODS="$SERVER_DIR/mods"
  if [[ ! -d "$SERVER_MODS" ]]; then
    warn "Server mods folder not found: $SERVER_MODS (skipping)"
  else
    log "Deploying to server..."
    deploy_jar "$JAR" "$SERVER_MODS" "Server  "
  fi
fi

# ── Step 6: Start / restart server ────────────────────────────────────────────
if [[ "$SERVER_DIR" != "none" && "$NO_SERVER" == false ]]; then
  START=false
  if $SERVER_WAS_RUNNING; then
    log "Restarting server..."
    START=true
  elif [[ -t 0 ]]; then
    read -rp "$(echo -e "${CYAN}[info]${NC}   Start the server now? (y/n): ")" _ans
    [[ "${_ans,,}" == "y" ]] && START=true
  else
    info "Non-interactive mode — server left stopped."
  fi

  if $START; then
    LAUNCHER=$(find_server_launcher "$SERVER_DIR")
    if [[ -z "$LAUNCHER" ]]; then
      err "No server launcher JAR found in $SERVER_DIR"
      exit 1
    fi
    mkdir -p "$SERVER_DIR/logs"
    cd "$SERVER_DIR"

    if $BACKGROUND; then
      # Background mode: detach with nohup, tail the log so the user can see startup
      nohup java -Xmx2G -jar "$(basename "$LAUNCHER")" nogui >> logs/server.log 2>&1 &
      BG_PID=$!
      sleep 4
      if kill -0 "$BG_PID" 2>/dev/null; then
        ok "Server running in background (PID $BG_PID). Logs: $SERVER_DIR/logs/server.log"
      else
        err "Server exited immediately. Last log lines:"
        tail -25 logs/server.log >&2
        exit 1
      fi
    else
      echo ""
      echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
      echo -e "${BOLD}  Minecraft server starting — type 'stop' to shut down${NC}"
      echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
      echo ""
      # exec replaces this script's process — the terminal stays open as the server console
      exec java -Xmx2G -jar "$(basename "$LAUNCHER")" nogui
    fi
  else
    info "Server left stopped."
  fi
fi

log "Done."
