#!/bin/bash

echo "Looking for running kraken-trader processes..."

# Find all java processes running kraken-trader
PIDS=$(ps aux | grep "kraken-trader" | grep -v grep | awk '{print $2}')

if [ -z "$PIDS" ]; then
    echo "No kraken-trader processes found running."
    exit 0
fi

echo "Found running processes with PIDs: $PIDS"
echo "Stopping all kraken-trader processes..."

for PID in $PIDS; do
    kill $PID
    echo "Stopped process $PID"
done

echo "All kraken-trader processes stopped."
