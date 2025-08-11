#!/bin/bash

# Find the latest jar file
JAR_FILE=$(find build/libs/ -name "kraken-trader-*.jar" | sort -V | tail -1)

if [ -z "$JAR_FILE" ]; then
    echo "Error: No kraken-trader jar file found in build/libs/"
    exit 1
fi

echo "Using jar file: $JAR_FILE"
echo "Starting Tier 2 - High Confidence trading..."
echo "Coins: RUNE/USD, FLOKI/USD, PYTH/USD"

java -jar "$JAR_FILE" buyLowSellHighStrategy RUNE/USD,FLOKI/USD,PYTH/USD 240 &

echo "Bot started with PID: $!"
