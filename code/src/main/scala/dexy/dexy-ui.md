# Dexy UI

Dexy protocol has two mandatory components, namely, the algorithmic bank and liquidity pool. 
The pool acts like an AMM (automated market maker) LP (liquidity pool), which is well-known for users of Spectrum, Uniswap, 
PancakeSwap etc, but with additional limitations and rules:

* redeeming LP tokens not possible when Dexy price is below 0.98 of reference (oracle) price, so 
  redeem option should be disabled in the UI
* 