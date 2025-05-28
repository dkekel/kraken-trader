# Kraken Trader

A crypto trading bot that integrates with Kraken APIs to execute trading strategies.

## Features

- Integration with Kraken APIs for real-time data and trading
- Multiple trading strategies
- Paper trading mode for testing strategies without real money
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

1. **Real Trading Mode (Default)**: Executes actual trades on the Kraken exchange using your API keys.
2. **Paper Trading Mode**: Simulates trading without making actual API calls to place orders. This mode is useful for testing strategies with live data but without risking real money.

#### Switching Between Trading Modes

To use paper trading mode, run the application with the `paper-trading` profile:

```
java -jar kraken-trader.jar --spring.profiles.active=paper-trading
```

To use real trading mode (default), either run without specifying a profile or explicitly set the default profile:

```
java -jar kraken-trader.jar
```

or

```
java -jar kraken-trader.jar --spring.profiles.active=default
```

#### Paper Trading Configuration

Paper trading mode starts with a default balance of 10,000 USD. You can customize this by modifying the `paper.trading.initial.balance` property in `application.yaml` or by setting it as a command-line parameter:

```
java -jar kraken-trader.jar --spring.profiles.active=paper-trading --paper.trading.initial.balance=50000
```

## Implementation Details

The application uses Spring profiles to switch between real trading and paper trading implementations:

- `KrakenApiService`: The real implementation that makes actual API calls to Kraken
- `PaperTradeKrakenApiService`: A simulated implementation that tracks paper balances and simulates trades

Both implementations share a common interface (`TradingApiService`) to ensure they can be used interchangeably.