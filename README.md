# Kraken Trader

A crypto trading bot that integrates with Kraken APIs to execute trading strategies.

## Features

- Integration with Kraken APIs for real-time data and trading
- Multiple trading strategies
- Paper trading mode for testing strategies without real money
- Flexible data sources: CSV historical data or live Kraken market data
- Configurable via Spring profiles

## Configuration

### API Keys

To use the real trading mode, you need to set your Kraken API keys as environment variables:

```
API_KEY=your_api_key
API_SECRET=your_api_secret
```

### Trading Modes

The application supports two trading modes:

1. **Real Trading Mode**: Executes actual trades on the Kraken exchange using your API keys.
2. **Paper Trading Mode (Default)**: Simulates trading without making actual API calls to place orders. This mode is useful for testing strategies with live data but without risking real money.

#### Switching Between Trading Modes

The default profile is `simulation-mode`, which is a convenience profile that includes both `paper-trading` and `csv-data` profiles. This allows you to run the application in a fully simulated environment with historical data.

To use paper trading mode (default), either run without specifying a profile or explicitly set the simulation-mode profile:

```
java -jar kraken-trader.jar
```

or

```
java -jar kraken-trader.jar --spring.profiles.active=simulation-mode
```

You can also activate the paper-trading profile directly if you want to combine it with a different data source:

```
java -jar kraken-trader.jar --spring.profiles.active=paper-trading
```

To use real trading mode, run the application with the `trading-mode` profile:

```
java -jar kraken-trader.jar --spring.profiles.active=trading-mode
```

#### Paper Trading Configuration

Paper trading mode starts with a default balance of 10,000 USD. You can customize this by modifying the `paper.trading.initial.balance` property in `application.yaml` or by setting it as a command-line parameter:

```
java -jar kraken-trader.jar --spring.profiles.active=paper-trading --paper.trading.initial.balance=50000
```

### Data Sources

The application supports two data sources for backtesting and strategy validation:

1. **CSV Historical Data (Default)**: Uses pre-downloaded historical data from CSV files.
2. **Live Kraken Market Data**: Fetches real-time and historical data directly from the Kraken API.

#### Switching Between Data Sources

To use live market data, run the application with the `live-data` profile:

```
java -jar kraken-trader.jar --spring.profiles.active=live-data
```

To use CSV file data (default), either run without specifying a data profile or explicitly set the csv-data profile:

```
java -jar kraken-trader.jar --spring.profiles.active=csv-data
```

#### Combining Profiles

You can combine trading mode and data source profiles:

```
# Paper trading with live data
java -jar kraken-trader.jar --spring.profiles.active=paper-trading,live-data

# Paper trading with CSV data (default - simulation-mode)
java -jar kraken-trader.jar

# Real trading with CSV data
java -jar kraken-trader.jar --spring.profiles.active=real-trading,csv-data
```

## Implementation Details

The application uses Spring profiles to switch between different implementations:

### Trading Implementations
- `KrakenApiService`: The real implementation that makes actual API calls to Kraken
- `PaperTradeKrakenApiService`: A simulated implementation that tracks paper balances and simulates trades

Both implementations share a common interface (`TradingApiService`) to ensure they can be used interchangeably.

### Data Source Implementations
- `CsvFileService`: Reads historical data from CSV files
- `MarketDataService`: Fetches historical data directly from the Kraken API

Both implementations share a common interface (`HistoricalDataService`) to ensure they can be used interchangeably.
