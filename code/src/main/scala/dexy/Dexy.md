# Dexy-USD StableCoin Design

This gives a high level overview of the Dexy-USD, which is a "StableCoin" pegged to USD. The design is extended from [this forum post](https://www.ergoforum.org/t/dexy-usd-simplest-stablecoin-design/1430) by kushti.
We use USD here because there is already an Erg-USD oracle deployed on the Ergo blockchain.

Below are the main design aspects of Dexy-USD.

1. **One-way tethering**: There is a minting (or "emission") contract that emits Dexy tokens (example DexyUSD) in a one-way swap using the oracle pool rate.
The swap is one-way in the sense that we can only buy Dexy tokens by selling ergs to the box. We cannot do the reverse swap. 
   
2. **Liquidify Pool**: The reverse swap, selling of Dexy tokens, is done via a Liquidity Pool (LP) which also permits buying Dexy tokens. The LP 
   primarily uses the logic of Uniswap V2. The difference is that the LP also takes as input the oracle pool rate and uses that to modify certain logic. In particular,
   redeeming of LP tokens is not allowed when the oracle pool rate is below a certain percent (say 90%) of the LP rate.
   
3. In case the oracle pool rate is higher than LP rate, then traders can do arbitrage by minting Dexy tokens from the emission box and 
   selling them to the LP. 
   
4. In case the oracle pool rate is lower than LP rate, then the Ergs collected in the emission box can be used to bring the rate back up by performing a swap.
   We call this the "top-up swap".
   
The swap logic is encoded in a **swapping** contract.

The LP uses a "cross-tracker" to keep track of the height at which the oracle pool rate dropped below the LP rate after a swap, that is 
the height at which the oracle pool rate was above the LP-box in rate but was below the LP-box out rate.
When this even happens, R5 of the LP box will store the height (within an error margin) at which this happened.
If the oracle pool rate again becomes higher than the LP rate, the register is set to Long.MaxValue (9223372036854775807).
This register can only be changed when the oracle pool and LP rates cross each other during an exchange. At all other times
this register must be preserved. 

The swap contract looks at R5 of the LP box and if the value is below a certain threshold (say 50 blocks) then the swap is allowed.
This implies that a swap is valid only when the oracle pool rate falls below the LP rate and stays below for at least 50 blocks.

## Draining Attack

Consider the following attack:

1. When profitable, mint some Dexy tokens and sell them on LP
2. When not profitable, top up LP using Dexy till it becomes profitable
3. Go to step 1 

The process will repeat until the emission box runs out of Ergs, and the attacker would have made profit.

In order to stop this attack, we can look at one or more of the following measures

1. Lock the minted Dexy tokens in a box until a certain time
2. Lock the Ergs in the emission contract until a certain time
3. Don't allow minting when its profitable

