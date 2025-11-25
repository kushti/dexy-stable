# CRUSH.md - Dexy Stablecoin Development Guide

## Build & Test Commands
- `sbt compile` - Compile the project
- `sbt test` - Run all tests
- `sbt testOnly dexy.TrackingSpec` - Run specific test class
- `sbt testOnly dexy.lp.LpMintSpec` - Run individual test files
- `cd paper && ./compile.sh` - Compile LaTeX paper

## Code Style Guidelines

### Imports
- Group imports with clear separation
- Specific imports for Ergo platform, Kiosk, and testing libraries
- Consistent import ordering

### Naming Conventions
- PascalCase for class names (`TrackingSpec`, `LpMintSpec`)
- camelCase for variables and methods
- Constants in UPPER_CASE
- Test properties use descriptive names

### Testing Patterns
- Use `PropSpec` with `Matchers` and `ScalaCheckDrivenPropertyChecks`
- Tests in `property("description")` blocks
- Mocked Ergo client setup: `createMockedErgoClient(MockData(Nil, Nil))`
- Transaction validation: `TxUtil.createTx()`
- Exception testing: `the[Exception] thrownBy { ... }`
- Success testing: `noException shouldBe thrownBy`

### Error Handling
- Use ScalaTest matchers for assertions
- Specific error message validation
- Blockchain context: `ergoClient.execute { implicit ctx: BlockchainContext => ... }`

### Project Structure
- **Contracts:** ErgoScript in `/contracts/`
- **Tests:** Scala tests in `/src/test/scala/dexy/`
- **Main code:** Scala in `/src/main/scala/`
- **Resources:** Test data in `/src/test/resources/`

## Dependencies
- Ergo Platform: v4.0.13-5251a78b-SNAPSHOT
- Kiosk: 1.0 (Ergo smart contract library)
- ScalaTest: 3.0.8, ScalaCheck: 1.14+
- Mockito: 2.23.4, Circe: 0.12.3

## Development Notes
- No linting/formatting configs found
- Use property-based testing with ScalaCheck
- Follow existing test patterns for consistency