# Dexy Stablecoin - Development Guide and Agent Specifications

This repository contains everything related to the Dexy stablecoin protocol and its testnet and mainnet deployments on the Ergo blockchain. This file is a guide for AI coding agents working in this repository; it is derived from the actual project contents (contracts, specs, tests) — keep it accurate when the project changes.

## Project Overview

Dexy is an algorithmic stablecoin protocol (deployed as DexyGold, pegged to gold, and USE/DexyUSD, pegged to the US dollar). It combines an oracle pool (delivering a reference price in nanoErgs per unit of the peg) with an algorithmic bank and a liquidity pool:

- **Bank**: the protocol's core contract. It holds reserves (Ergs) and Dexy tokens in circulation and is spent through dedicated action contracts: `arbmint.es` (arbitrage mint, allowed when LP price is above oracle price), `freemint.es` (free mint against bank reserves), `intervention.es` (bank swaps Ergs for Dexy in the LP to defend the peg), `payout.es` (pays out reserves when overcollateralized) and `buyback.es` (sends a 0.2% share of each mint's fee to buy back the oracle reward token — see `spec/gort.md` for the GORT/DORT tokenomics).
- **Liquidity Pool (LP)**: a Uniswap V2-style AMM for Dexy/Erg, but split into action contracts (like the bank) rather than one monolithic script: `lp/pool/main.es`, `mint.es`, `redeem.es`, `swap.es`, `extract.es` (extract-to-the-future / release-extracted). Differences from Spectrum/ErgoDex: 2% redemption fee, redemption disabled while the LP price is below 0.98 of the oracle price, and intervention/extract actions where the bank interacts with the LP. `lp/proxy/` contains user-facing proxy contracts (deposit, redeem, swap-buy/sell).
- **Tracking contract** (`tracking.es`): a "tracker" box that monitors the ratio of LP rate to oracle rate and can be in a triggered or reset state. Registers R4 (numerator), R5 (denominator), R6 (isBelow), R7 (trigger height, `Int.MaxValue` when reset). Three live trackers: 95/100 below (extract-to-future), 98/100 below (arbitrage mint / intervention), 101/100 above (release).
- **Update mechanism** (`bank/update/update.es`, `bank/update/ballot.es`): bank, extract and intervention scripts may be updated by a vote; 3 of 5 update/ballot NFT votes are needed (3 update NFTs are issued to allow parallel execution).
- `gort-dev/emission.es` and `hodlcoin/hodlcoin.es` are additional token contracts used in the ecosystem.

The original whitepaper design (emission contract + swapping contract) is described in `spec/spec.md`; the current deployed design is the bank + split-LP design documented in the per-token deployment specs.

## Technology Stack

- Scala 2.13.12, sbt 1.5.2 (`project/build.properties`), project name "dexy" v0.1 (`build.sbt`).
- Ergo Platform: `ergo-appkit` 5.0.4, `ergo-wallet` 5.0.20 (both from Maven Central), Kiosk 1.0.0 (Ergo smart contract library used to build test transactions). Note: `ergo-core` and old Kiosk releases disappeared from public repos (Sonatype OSSRH shutdown), so `ergo-core` was dropped — the `offchain` package now uses `sigma-state`/`ergo-wallet` types (`UnsignedErgoLikeTransaction`, `ErgoUnsafeProver`, ...). Kiosk is published locally from the master of `github.com/ergoplatform/kiosk` (`sbt publishLocal`, plus `dependencyOverrides += scrypto 2.3.0` to replace the purged snapshot both here and in kiosk's build). Two dexy-specific patches are applied to that local kiosk build (in `ergo/package.scala` and `tx/TxUtil.scala`): the `KioskBoolean` type and a `creationHeight: Option[Int]` passthrough on `KioskBox`/`TxUtil.createTx`, both of which the dexy test-suite requires; kiosk master has neither.
- ScalaTest 3.0.8 + ScalaCheck 1.14 (property-based tests), Mockito 2.23.4, Circe 0.12.3, scalaj-http 2.4.2, okhttp3 mockwebserver 3.14.9.
- Papers: LaTeX sources in `paper-draft/` (IEEEtran) and `paper-lipics/` (LIPIcs), each with a `compile.sh` that runs pdflatex/bibtex (requires `pdflatex` and `bibtex` installed).

## Build & Test Commands

```bash
sbt compile                                   # compile
sbt test                                      # run all tests
sbt testOnly dexy.TrackingSpec                # one test class
sbt testOnly dexy.lp.LpMintSpec               # one test file
sbt testOnly "dexy.bank.*"                    # all bank tests
cd paper-lipics && ./compile.sh               # compile the LaTeX paper
```

Tests are the only executable checks; there is no CI configuration in the repo. There is no coverage plugin configured in `build.sbt` (the `sbt coverage test` command from older docs will not work unless the scoverage plugin is added).

Note: test token IDs and constants are hardcoded in test specs and must match the substitution maps (see below); running tests does not require a node or network access (mocked client).

## Code Architecture

### Contracts (`contracts/`) — ErgoScript

- `bank/`: `bank.es` plus action contracts `arbmint.es`, `freemint.es`, `intervention.es`, `payout.es`, `buyback.es`, and `update/` (`update.es`, `ballot.es`). Each contract file starts with a header comment documenting the box layout (tokens, registers) and every allowed transaction pattern (inputs / outputs / data-inputs). Preserve and update these headers when editing contracts.
- `lp/pool/`: `main.es` (LP box), `mint.es`, `redeem.es`, `swap.es`, `extract.es`. `lp/proxy/`: user-facing proxies (`Deposit.es`, `Redeem.es`, `SwapBuyV1/V2.es`, `SwapSell/V1.es`).
- `tracking.es`: tracker with ratio registers described above.
- Contracts are templates: identifiers such as `$lpNFT`, `$oracleNFT`, `$intMax`, `$feeNumLp` are placeholders substituted at load time (see below).

### Main Scala (`src/main/scala/`)

- `dexy/chainutils/` — contract loading and deployment helpers:
  - `NetworkTokenIds.scala`: trait listing all token/NFT IDs (oracle, LP, bank, tracking, update tokens).
  - `DexyGoldSpec.scala`: `MainnetDexyGoldTokenIds` / `TestnetTokenIds` (mainnet/testnet ID sets) and `object DexyGoldSpec extends ContractUtils` — loads each `.es` file from `contracts/`, substitutes `$`-placeholders via `nftDictionary` / `defaultSubstitutionMap`, compiles ErgoTrees (`ScriptUtil`), and contains `main` plus deployment-request builders (`bankContractDeploymentRequest`, `trackingContractDeploymentRequest`, etc.).
  - `UseSpec.scala`: the same for the USE (DexyUSD) deployment — `MainnetUseTokenIds` and `UseSpec`.
  - `ContractUtils.scala`: `readContract`/`substitute` — plain string replacement of `$key` placeholders in contract sources; paths are relative to `contracts/`, so tests must run from the repo root.
- `offchain/` — bot code that builds and signs real transactions against a node (`DexyLpSwap.scala` is an `App`, `OffchainUtils.scala`, `BuyBackUtils.scala`, `GortDevUtils.scala`). These contain local node URLs and keystore paths; they are operator tooling, not tests.
- `simulation/` — `DexySimulation.scala`, a small economic model/simulation of the bank and LP.

### Tests (`src/test/`)

- `scala/dexy/`: the main protocol test suites — `TrackingSpec`, `bank/` (`ArbMintSpec`, `FreeMintSpec`, `InterventionSpec`, `BuybackSpec`, `PayoutSpec`, `UpdateSpec`), `lp/` (`LpMintSpec`, `LpRedeemSpec`, `LpSwapSpec`, `ExtractSpec`, `ReverseExtractSpec`), plus `Common.scala` (shared fake tx IDs, dummy token IDs, `fakeScript = "sigmaProp(true)"`) and `HttpClientTesting.scala`.
- `scala/gort/`, `scala/hodl/`, `scala/oracles/`: tests for the auxiliary contracts.
- `java/dexy/`: `MockedErgoClient` / `FileMockedErgoClient` — the mocked blockchain client implementation.
- `resources/mockwebserver/`: canned node/explorer JSON responses used by the mocked client.

### Specs and proposals

- `spec/spec.md`: original protocol design (emission + swapping contracts, superseded by the bank design for deployments).
- `spec/deployment-gold.md`, `spec/deployment-usd.md`: mainnet deployment notes — all real token IDs, fee/parameter values, and the update-protocol procedure. When token IDs change, update these files, the `*TokenIds` objects in `src/main/scala/dexy/chainutils/`, and the tests together.
- `spec/gort.md`: GORT (Gold Oracle Reward Token) tokenomics; `spec/dexy-ui.md`: UI-facing protocol rules.
- `Uips/`: Dexy (USE) Improvement Proposals — `UIP-000-template.md`, `UIP-001-balancing-interventions.md`, `README.md` describing the UIP process and required structure.

## Code Style Guidelines

### Contracts (ErgoScript)

- Every contract starts with a structured header comment: box role, TOKENS, REGISTERS, and TRANSACTIONS tables (input | output | data-input). Update the header when changing the contract.
- Use `sigmaProp(...)` for the final condition; guard everything with `&&`-joined named `val`s (`validSuccessor`, `validOracleBox`, ...).
- Contracts identify counterpart boxes by the NFT in `tokens(0)`; never trust box position alone without the NFT check.
- Never use floating point; use integer arithmetic with explicit numerator/denominator parameters and BigInt widening (`toBigInt`) to avoid overflow.
- Keep the oracle-rate normalization in mind: the oracle pool delivers nanoErgs per USD; contracts divide by 1000 because the Dexy token has 3 decimals (`oracleRateXY = oracleBox.R4[Long].get / 1000L`).

### Scala

- Match the existing two deployment flavors (Gold vs USE): token IDs in `NetworkTokenIds` implementations, contract scripts as `lazy val`s loaded through `ContractUtils.readContract`.
- Test classes are `PropSpec with Matchers with ScalaCheckDrivenPropertyChecks with HttpClientTesting with Common`, with one `property("descriptive sentence")` per scenario.
- Success case: build boxes with `ctx.newTxBuilder()...convertToInputWith(fakeTxIdN, fakeIndex)`, assert `noException shouldBe thrownBy { TxUtil.createTx(...) }`; failure case: assert `the[Exception] thrownBy { ... }` with a `.getMessage shouldBe ...` where the suite does so.
- Use the Kiosk helpers (`KioskLong`, `KioskInt`, `KioskBox`, ...) and `TxUtil.createTx` rather than hand-rolled transaction builders in tests.

## Testing Instructions

Testing philosophy is documented in `src/test/scala/dexy/README.md`. For every box spent by a transaction, each spec tests, per property:

1. Cannot change the address of the corresponding output box
2. Cannot change tokens of the corresponding output box (except when allowed)
3. Cannot decrease the Ergs value (except when allowed)
4. Cannot add junk tokens
5. Cannot add junk registers
6. Cannot accept other boxes (inputs, data inputs) with wrong NFTs
7. App-specific invariants (e.g. "LP tokens should not reduce during intervention")

Tests run entirely against `createMockedErgoClient(MockData(Nil, Nil))` — no live node is needed. Threshold tests deliberately place rates on both sides of the boundary (e.g. 98% tracker trigger/reset) and check both positive and negative paths.

## Key Parameters (mainnet, see deployment specs for source of truth)

- LP swap fee: 0.3% (`feeNum = 3`, `feeDenom = 1000`)
- LP redemption fee: 2%; redemption disabled when LP price < 0.98 × oracle price
- Tracking ratios: 95/100 (below, extract-to-future), 98/100 (below, arbitrage mint/intervention), 101/100 (above, release); trigger-height error threshold: 3 blocks
- Intervention: 1% of bank reserves when oracle price falls below 98% of target (UIP-001 proposes changing this basis/frequency)
- Bank mint fee share to buyback contract: 0.2%; payout sends excess reserves when overcollateralization is above 1000%
- Update voting: 3 of 5 votes; 3 update NFTs issued to allow parallel execution
- Bank/extract activation threshold (minBankNanoErgs in `extract.es`): 10,000 ERG
- Dexy token has 3 decimals; oracle rate is normalized by dividing by 1000

## Security Considerations

- These are financial smart contracts: any change to thresholds, fee numerators/denominators, NFT IDs, or register layouts in `contracts/` is consensus- and fund-critical. Verify against `spec/deployment-*.md` and the UIPs, and make sure the corresponding Scala specs and tests are updated and passing.
- Never hardcode real token IDs in contracts — contracts take them as `$`-substituted constants from the `*TokenIds` objects; keep testnet and mainnet IDs strictly separated.
- The `offchain/` tools embed operator node URLs and keystore paths and sign with local secrets; do not commit real secrets, and treat that code as privileged tooling.
- `contracts/` scripts are deployed by issuing NFTs and paying boxes to the compiled ErgoTrees; an incorrect NFT assignment makes a box unidentifiable/unspendable by the protocol.

## Known TODOs (from README)

- Final contracts audit
- Check test coverage
