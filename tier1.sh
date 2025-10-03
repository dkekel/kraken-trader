#!/bin/bash

# Check if interval argument is provided
if [ -z "$1" ]; then
    echo "Usage: $0 <interval>"
    exit 1
fi

INTERVAL=$1

# Find the latest jar file
JAR_FILE=$(find build/libs/ -name "kraken-trader-*.jar" | sort -V | tail -1)

if [ -z "$JAR_FILE" ]; then
    echo "Error: No kraken-trader jar file found in build/libs/"
    exit 1
fi

echo "Using jar file: $JAR_FILE"
echo "Starting Tier 1 - Highest Confidence trading..."
echo "Coins: AR/USD, ATOM/USD, KAS/USD, ETH/USD"
echo "Interval: $INTERVAL"

java -jar "$JAR_FILE" buyLowSellHighStrategy AR/USD,ATOM/USD,KAS/USD,ETH/USD "$INTERVAL" &

echo "Bot started with PID: $!"