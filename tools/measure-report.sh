#!/usr/bin/env bash
# THROWAWAY (wayfinder #23): summarise a dev-client log written by MeasurementLogger.
#
#   tools/measure-report.sh [logfile...]      default: the Windows dev client log
#
# Prints, per section: the SETUP line, the arm-length fit (D vs pitch, all CAM samples),
# follow height and scale ratio per zoom varc, the Landing Tile per Step transition, every
# scene-object CLICK with its clickbox area, every OBJ spawn, tile HEIGHTS, and the SELFCHECK
# lines. Paste the output into the ticket.
set -euo pipefail

WIN_DIR="${RL_DEV_DIR:-/mnt/c/Users/zantox/runelite-dev}"
if [ "$#" -eq 0 ]; then
    set -- "$WIN_DIR/dev-client.log"
fi

lines() { local kind="$1"; shift; grep -h "MEASURE $kind " "$@" 2>/dev/null | tr -d '\r' | sed 's/.*MEASURE /MEASURE /' || true; }

section() { printf '\n== %s ==\n' "$1"; }

section SETUP
lines SETUP "$@" | tail -n 3

section "CAM: arm length D vs pitch (JAU14), least squares over all samples"
lines CAM "$@" | awk '
{
    for (i = 1; i <= NF; i++) { split($i, kv, "="); v[kv[1]] = kv[2] }
    p = v["pitch"] + 0; d = v["armMeasured"] + 0
    n++; sx += p; sy += d; sxx += p * p; sxy += p * d
    if (v["heightFactor"] != "") hf = v["heightFactor"]
}
END {
    if (n < 2) { print "  fewer than 2 samples"; exit }
    a = (n * sxy - sx * sy) / (n * sxx - sx * sx); b = (sy - a * sx) / n
    printf "  samples=%d  D = %.4f * pitch14 + %.1f   (research expects 0.375 * pitch14 + 600, times heightFactor=%s)\n", n, a, b, hf
    printf "  a/heightFactor=%.4f  b/heightFactor=%.1f\n", a / hf, b / hf
}'

section "CAM: per zoom varc74: follow height, scale vs predicted, arm ratio (min/max over samples)"
lines CAM "$@" | awk '
{
    for (i = 1; i <= NF; i++) { split($i, kv, "="); v[kv[1]] = kv[2] }
    z = v["varc74"]; f = v["followHeight"] + 0; r = v["scaleRatio"] + 0; ar = v["armRatio"] + 0
    if (!(z in n)) { fmin[z] = f; fmax[z] = f; rmin[z] = r; rmax[z] = r; amin[z] = ar; amax[z] = ar; sc[z] = v["scale"]; pmin[z] = v["pitch"]; pmax[z] = v["pitch"] }
    n[z]++
    if (f < fmin[z]) fmin[z] = f; if (f > fmax[z]) fmax[z] = f
    if (r < rmin[z]) rmin[z] = r; if (r > rmax[z]) rmax[z] = r
    if (ar < amin[z]) amin[z] = ar; if (ar > amax[z]) amax[z] = ar
    if (v["pitch"] + 0 < pmin[z] + 0) pmin[z] = v["pitch"]; if (v["pitch"] + 0 > pmax[z] + 0) pmax[z] = v["pitch"]
}
END {
    printf "  %-8s %-6s %-8s %-18s %-18s %-18s %s\n", "varc74", "n", "scale", "followHeight", "scaleRatio", "armRatio", "pitch"
    for (z in n) printf "  %-8s %-6d %-8s %-18s %-18s %-18s %s..%s\n", z, n[z], sc[z], fmin[z] "..." fmax[z], rmin[z] "..." rmax[z], amin[z] "..." amax[z], pmin[z], pmax[z]
}' | sort -k1,1n

section "CAM: pitch range seen (min/max pitch, pitchTarget)"
lines CAM "$@" | awk '
{
    for (i = 1; i <= NF; i++) { split($i, kv, "="); v[kv[1]] = kv[2] }
    p = v["pitch"] + 0; t = v["pitchTarget"] + 0
    if (NR == 1) { pmin = p; pmax = p; tmin = t; tmax = t }
    if (p < pmin) pmin = p; if (p > pmax) pmax = p; if (t < tmin) tmin = t; if (t > tmax) tmax = t
}
END { printf "  pitch %d..%d   pitchTarget %d..%d\n", pmin, pmax, tmin, tmax }'

section "CAM: focal residual (focal - player local), max abs"
lines CAM "$@" | awk '
{
    for (i = 1; i <= NF; i++) { split($i, kv, "="); v[kv[1]] = kv[2] }
    split(v["focalResidual"], fr, ","); x = fr[1] + 0; y = fr[2] + 0
    if (x < 0) x = -x; if (y < 0) y = -y
    if (x > mx) mx = x; if (y > my) my = y
}
END { printf "  max |dx|=%.1f  max |dy|=%.1f\n", mx, my }'

section "STEP transitions with player tile (Landing Tile candidates)"
lines STEP "$@"

section "CLICK: scene-object clicks (tile = Landing Tile of the Hop before)"
lines CLICK "$@" | sed -E 's/ local=.*//'

section "CLICK: clickbox area per object id (min/max px^2, count)"
lines CLICK "$@" | awk '
{
    for (i = 1; i <= NF; i++) { split($i, kv, "="); v[kv[1]] = kv[2] }
    id = v["id"]; a = v["clickboxArea"] + 0
    if (!(id in n)) { amin[id] = a; amax[id] = a }
    n[id]++
    if (a < amin[id]) amin[id] = a; if (a > amax[id]) amax[id] = a
}
END { for (id in n) printf "  id=%s n=%d area=%.0f..%.0f\n", id, n[id], amin[id], amax[id] }' | sort

section "OBJ spawns (last per id)"
lines OBJ "$@" | awk '{ for (i = 1; i <= NF; i++) if ($i ~ /^id=/) last[$i] = $0 } END { for (k in last) print "  " last[k] }' | sort

section HEIGHTS
lines HEIGHTS "$@" | tail -n 4

section "SELFCHECK (ghost vs real clickbox; dist is the centroid delta in px)"
lines SELFCHECK "$@"
