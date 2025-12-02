# Dexy Stablecoin - Development Guide and Agent Specifications

Dexy is a blockchain-based stablecoin protocol running on the Ergo platform. This guide describes the system architecture, development practices, and operational agents.

## System Overview

The Dexy stablecoin protocol consists of multiple interconnected smart contracts that work together to maintain price stability through algorithmic mechanisms:

- **Liquidity Pool (LP)**: A Uniswap V2-like AMM that provides trading between Dexy tokens and Ergs
- **Emission Contract**: One-way minting contract that allows purchasing Dexy tokens at oracle-determined rates
- **Tracking Contracts**: Monitors price relationships and triggers specific actions when thresholds are met
- **Swapping Contract**: Facilitates top-up swaps to maintain price stability
- **Bank Contracts**: Algorithmic bank that manages minting and protocol operations

## Build & Test Commands

### Setup and Compilation
- `sbt compile` - Compile the project
- `sbt clean` - Clean compiled artifacts
- `sbt update` - Update dependencies

### Running Tests
- `sbt test` - Run all tests
- `sbt testOnly dexy.TrackingSpec` - Run specific test class
- `sbt testOnly dexy.lp.LpMintSpec` - Run individual test files
- `sbt testOnly dexy.*` - Run all Dexy tests
- `sbt coverage test` - Run tests with coverage report

### Paper Compilation
- `cd paper-lipics && ./compile.sh` - Compile LaTeX paper

## Code Architecture

### Contracts Directory
- **`/contracts/`**: ErgoScript smart contracts
  - `tracking.es`: Price tracking and alert mechanisms
  - `/lp/`: Liquidity pool contracts
  - `/bank/`: Bank contract implementations
  - `/gort-dev/`, `/hodlcoin/`: Additional token implementations

### Source Code Structure
- **`/src/main/scala/`**: Core Scala application code (if any)
- **`/src/test/scala/dexy/`**: Test specifications for Dexy protocol
  - `TrackingSpec`: Tests for tracking contract logic
  - `lp/`: Liquidity pool tests
  - `oracles/`: Oracle-related tests
  - `gort/`, `hodl/`: Additional protocol tests

### Specification Files
- **`/spec/`**: Protocol specifications and deployment details
  - `spec.md`: Main protocol specification
  - `deployment-usd.md`: Deployment details for DexyUSD
  - `deployment-gold.md`: Deployment details for DexyGold

## Code Style Guidelines

### Imports
- Group imports with clear separation
- Specific imports for Ergo platform, Kiosk, and testing libraries
- Consistent import ordering: standard libraries first, then project-specific imports

### Naming Conventions
- PascalCase for class names (`TrackingSpec`, `LpMintSpec`)
- camelCase for variables and methods
- Constants in UPPER_CASE
- Test properties use descriptive names
- Contract identifiers use NFT suffixes (e.g., `lpNFT`, `oracleNFT`, `tracking98NFT`)

### Testing Patterns
- Use `PropSpec` with `Matchers` and `ScalaCheckDrivenPropertyChecks`
- Tests in `property("description")` blocks
- Mocked Ergo client setup: `createMockedErgoClient(MockData(Nil, Nil))`
- Transaction validation: `TxUtil.createTx()`
- Exception testing: `the[Exception] thrownBy { ... }`
- Success testing: `noException shouldBe thrownBy`
- Blockchain context: `ergoClient.execute { implicit ctx: BlockchainContext => ... }`

### Smart Contract Patterns
- Use `sigmaProp()` for contract conditions
- Validate token IDs with `fromBase64()` encoded values
- Check data inputs and outputs thoroughly
- Use proper threshold checks and error margins
- Implement cross-counter for rate crossing detection

## Key Components and Agents

### Core Contracts
1. **Emission Contract**: Enables one-way purchase of Dexy tokens at oracle rates
2. **Liquidity Pool**: Uniswap V2-style AMM with cross-counter and oracle integration
3. **Tracking Contracts**: Three types - 95% (for extract-to-future), 98% (for arbitrage mint), 101% (for release)
4. **Swapping Contract**: Top-up swap mechanism for price stability
5. **Bank Contracts**: Algorithmic minting and management

### Operational Agents
- **Oracle Providers**: Supply price data to oracle pools
- **Arbitrage Bots**: Monitor price differences for profitable trading
- **Liquidity Providers**: Supply assets to liquidity pools
- **Swap Executors**: Execute top-up swaps when triggered
- **Protocol Updaters**: Manage protocol updates (with voting mechanisms)

## Dependencies

- Ergo Platform: v5.0.20
- Kiosk: 1.0.2 (Ergo smart contract library)
- Ergo Appkit: 5.0.4
- ScalaTest: 3.0.8
- ScalaCheck: 1.14+
- Mockito: 2.23.4
- Circe: 0.12.3

## Development Workflow

### Testing Philosophy
- Property-based testing using ScalaCheck for comprehensive validation
- Mocked blockchain context for reliable test execution
- Validation of both positive and negative scenarios
- Edge case testing around threshold conditions

### Deployment Strategy
- Multiple NFTs for different contract types
- Update mechanisms with voting systems
- Parallel execution support through multiple tokens
- Careful token management to ensure contract identification

## Key Parameters
- Fee structure: 0.3% for liquidity pool swaps
- Tracking thresholds: 95%, 98%, 101% ratios
- Waiting periods: 20 blocks for swap completion
- Error margins: 3 blocks for tracking tolerance
- Redemption fees: 2% for liquidity pool redemptions