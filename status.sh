#!/bin/bash

echo "=== Kraken Trader Status ==="
echo

# Check for running processes
PROCESSES=$(ps aux | grep "kraken-trader" | grep -v grep)

if [ -z "$PROCESSES" ]; then
    echo "❌ No kraken-trader processes currently running."
else
    echo "✅ Running kraken-trader processes:"
    echo "$PROCESSES" | while read line; do
        PID=$(echo $line | awk '{print $2}')
        COMMAND=$(echo $line | awk '{for(i=11;i<=NF;i++) printf "%s ", $i; print ""}')
        echo "  PID: $PID - $COMMAND"
    done
fi

echo
echo "Available scripts:"
echo "  ./tier1.sh      - Highest confidence (AR, ATOM, KAS, ETH)"
echo "  ./tier2.sh      - High confidence (RUNE, FLOKI, PYTH)"
echo "  ./conservative.sh - Safe starter pack"
echo "  ./aggressive.sh - Maximum growth potential"
echo "  ./balanced.sh   - Balanced portfolio"
echo "  ./stop_bot.sh   - Stop all running bots"
echo "  ./status.sh     - Show this status"
