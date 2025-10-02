#!/bin/sh
set -e

OSRM_FILE=/data/hcm.osm.pbf
OSRM_BASE=/data/hcm.osrm

if [ ! -f "${OSRM_BASE}.partition" ]; then
  echo "[OSRM] Preprocessing data (first run)..."
  osrm-extract -p /opt/car.lua $OSRM_FILE
  osrm-partition $OSRM_BASE
  osrm-customize $OSRM_BASE
else
  echo "[OSRM] Preprocessed files found, skipping extract/partition/customize."
fi

echo "[OSRM] Starting routing engine..."
exec osrm-routed --algorithm mld $OSRM_BASE
