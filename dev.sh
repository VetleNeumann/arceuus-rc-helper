#!/usr/bin/env bash
# Dev loop: build the shadow jar in WSL, copy it to the Windows RuneLite dev
# folder, and control the dev client there through tools/dev.ps1.
#
#   ./dev.sh            build shadow jar, copy to Windows, restart dev client
#   ./dev.sh build      build + copy only
#   ./dev.sh start|stop|restart|status
#   ./dev.sh log        tail the dev client's stdout/stderr logs
#
# Override the Windows folder with RL_DEV_DIR (a WSL path under /mnt/c).
# See docs/DEV-LOOP.md for the one-time setup.
set -euo pipefail
cd "$(dirname "$0")"

WIN_DIR="${RL_DEV_DIR:-/mnt/c/Users/zantox/runelite-dev}"
JAR_NAME=arceuus-rc-helper-dev.jar

ps_ctl() {
    cp tools/dev.ps1 "$WIN_DIR/dev.ps1"
    powershell.exe -NoProfile -ExecutionPolicy Bypass \
        -File "$(wslpath -w "$WIN_DIR/dev.ps1")" -Action "$1" -JarName "$JAR_NAME" | tr -d '\r'
}

build() {
    mkdir -p "$WIN_DIR"
    ./gradlew shadowJar -q
    cp build/libs/arceuus-rc-helper-*-all.jar "$WIN_DIR/$JAR_NAME"
    echo "deployed -> $WIN_DIR/$JAR_NAME"
}

case "${1:-deploy}" in
    deploy)  build; ps_ctl restart ;;
    build)   build ;;
    start|stop|restart|status) ps_ctl "$1" ;;
    log)     tail -n 50 -F "$WIN_DIR/dev-client.log" "$WIN_DIR/dev-client.err.log" ;;
    *)       echo "usage: $0 [deploy|build|start|stop|restart|status|log]" >&2; exit 2 ;;
esac
