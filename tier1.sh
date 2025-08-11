#!/bin/bash

# Find the latest jar file
JAR_FILE=$(find build/libs/ -name "kraken-trader-*.jar" | sort -V | tail -1)

if [ -z "$JAR_FILE" ]; then
    echo "Error: No kraken-trader jar file found in build/libs/"
    exit 1
fi

echo "Using jar file: $JAR_FILE"
echo "Starting Tier 1 - Highest Confidence trading..."
echo "Coins: AR/USD, ATOM/USD, KAS/USD, ETH/USD"

java -jar "$JAR_FILE" buyLowSellHighStrategy AR/USD,ATOM/USD,KAS/USD,ETH/USD 240 &

echo "Bot started with PID: $!"