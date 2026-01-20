# UIP-001: Balancing the Interventions

* Author(s): richie
* Status: Proposed
* Created: 2026-01-20
* License: CC0

## Description

This UIP proposes changes to the intervention mechanism in the USE stablecoin protocol to prevent information asymmetry exploitation and preserve reserves. The current intervention system allows traders to front-run bank interventions when ERG prices drop rapidly, leading to inefficient reserve usage.

## Motivation

When the ERG price on exchanges drops rapidly, traders can exploit information asymmetry with the bank by buying USE tokens from the LP in anticipation of a bank intervention. This effectively front-runs the bank—over a short period of time—thereby draining reserves.

The more aggressive the bank's intervention, the greater the impact these actions have on reserves.

Currently, an intervention is triggered when the oracle price falls below 98% of the target price, and the bank uses 1% of its reserves.

In situations where the bank holds more ERG than the liquidity pool, this intervention can become excessively strong, resulting in greater reserve losses.

## Specification

This proposal includes three key changes to the intervention mechanism:

1. **Change intervention basis**: Change the intervention amount from a percentage of bank reserves to a percentage of LP reserves. This makes each intervention's size predictable and prevents it from becoming disproportionate when there is an imbalance between the bank and the LP.

2. **Reduce intervention size**: Reduce the intervention size from 1% to 0.5% of LP reserves.

3. **Increase intervention frequency**: Increase the intervention frequency by a factor of 2, allowing for more frequent but smaller interventions.

The intervention trigger remains the same (when oracle price falls below 98%), but the execution parameters change to distribute the same total support more evenly over time.

## Rationale

While the bank is technically committing the same total amount of ERG to support the price over time, the support is now distributed more evenly through smaller, more frequent interventions. This approach:

- Reduces the impact of front-running opportunities
- Preserves reserves more effectively than the initial implementation
- Makes intervention sizes predictable regardless of bank/LP balance disparities
- Maintains the same overall market support while improving efficiency

## Backwards Compatibility

This change affects the intervention contract logic and may require redeployment of the intervention contract. Existing tokens and positions should remain unaffected, but the intervention behavior will change according to the new parameters.

## Reference Implementation

The intervention contract will need to be updated to:
- Calculate intervention amounts based on LP reserves instead of bank reserves
- Adjust the intervention size parameter from 1% to 0.5% of LP reserves
- Implement logic for increased intervention frequency