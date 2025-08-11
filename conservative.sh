#!/bin/bash

# Find the latest jar file
JAR_FILE=$(find build/libs/ -name "kraken-trader-*.jar" | sort -V | tail -1)

if [ -z "$JAR_FILE" ]; then
    echo "Error: No kraken-trader jar file found in build/libs/"
    exit 1
fi

echo "Using jar file: $JAR_FILE"
echo "Starting Conservative Starter Pack..."
echo "Coins: ETH/USD, AR/USD, ATOM/USD"

java -jar "$JAR_FILE" buyLowSellHighStrategy ETH/USD,AR/USD,ATOM/USD 240 &

echo "Bot started with PID: $!"
