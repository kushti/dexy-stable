# Dexy UI

Dexy protocol has two mandatory components, namely, the algorithmic bank and liquidity pool. 

## Liquidity pool UI

The pool acts like an AMM (automated market maker) LP (liquidity pool), which is well-known for users of Spectrum, Uniswap, 
PancakeSwap etc, but with additional limitations and rules. For UI, only following two are important probably: 

* redeeming LP tokens not possible when Dexy price is below 0.98 of reference (oracle) price, so 
  redeem option should be disabled in the UI
* there is 2 % redemption fee

Other LP-specific rules will be done via offchain bots.

# Bank UI

Bank UI will show bank status:
* current bank reserves
* total amount of Dexy in circulation
* latest operations
* oracle and LP price
* possibility of minting Dexy and amount which could be minted

And the bank allows for minting Dexy when it is allowed by the protocol.

According to the protocol, minting is possible:

* when DexyGold token price in LP is higher than oracle price, the bank allows to mint number of tokens
 enough to equalize the prices (basically, providing liquidity for arbitrage)
* in addition, it is allowed to mint up to 1 % of Dexy tokens in the LP per day freely 
