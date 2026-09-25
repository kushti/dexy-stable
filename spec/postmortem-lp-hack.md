Postmortem: USE & DexyGold LP Hack (September 2026)
=====================================================

Incident summary
----------------

On September 7th, 2026, the USE (DexyUSD) protocol liquidity pool was drained by a hacker, and shortly after the
DexyGold liquidity pool was emptied in the same way. This document is a technical postmortem based on the public
incident report [1]; its purpose is to record the root cause in this repository's contract templates, document the
exploit mechanics, audit the remaining contracts for the same vulnerability class, and specify (without applying)
the recommended fixes.

Timeline and on-chain facts (from [1]):

* The hacker's address was funded from MEXC in transaction
  `f0d551d886072ce3c75da203c10b4c7a01f8ad6b6cb836e15f656d648fe50325`.
* Three tokens were minted by the hacker in transactions
  `f951a80e48ca86b1cdff2b471c12206c46e131df7ff03f43d99414af1c7bd2d4`,
  `94e9396a225d627f8609cbf35d93e5533ace01849156d58ec0faae6fdd6b5682` and
  `7f18ca9595e380124989368030cce8f495e808702201e79ceafb6938beea3732` (used to construct the fake "LP" input,
  see below).
* The exploit was then executed repeatedly against the USE LP swap contract. 25,000 ERG were sent to a MEXC
  deposit address in transaction
  `471ead780bee74f3a3cc6bba1cf86f6ad175613d14834b029af2d0755db6b90a`, and 180,000 ERG to a Kucoin deposit
  address in transaction
  `c1fcfedb7f184af4c3c54e912343dee9d44ddf26d1725d2852786d914cc6ac66`.
* The DexyGold LP was then emptied with the same technique, and 83,118 ERG were sent to the same Kucoin deposit
  address in transaction
  `59dbc1c54e7926234fec1ee50e850354ac91c3f739e9a5c4c1460991998e0a80`.

The incident report summarizes the root cause as follows: "the absence of LP input NFT check in expected position
was used to simulate LP box to swap action, with LP box put in not expected position (LP contract also did not
check its position)". The next sections substantiate this against the contract sources in this repository.


Root cause
----------

The LP is not a single monolithic script; it is split into a pool box (`contracts/lp/pool/main.es`) and action
boxes (swap, mint, redeem, extract) that validate the pool box by referring to it **by position**. Two
independent gaps combine into the exploit:

1. **The swap action contract never checks that INPUTS(0) is the real LP box.**
   In `contracts/lp/pool/swap.es:48-49`:

       val lpBoxIn = INPUTS(lpBoxInIndex)   // lpBoxInIndex = 0
       val lpBoxOut = OUTPUTS(lpBoxOutIndex) // lpBoxOutIndex = 0

   The constant-product-with-fee invariant (`validSwap`, swap.es:72-78) is verified against whatever box happens
   to sit at index 0. There is no check of the LP NFT (`lpBoxIn.tokens(0)._1 == lpNFT`), and no check of the
   pool script. An attacker-controlled box with a trivially-true script therefore passes the swap math: with
   zero deltas the `else` branch degenerates to `0 >= 0`, i.e. a no-op "swap" is accepted.
   The same positional-only reference exists in `contracts/lp/pool/mint.es:15-16` and
   `contracts/lp/pool/redeem.es:19-23`; both are exploitable in the identical way (the hack used only swap).

2. **The pool contract never binds itself to a position.**
   `contracts/lp/pool/main.es:86-131` validates that *some* input carries one of the action NFTs
   (`validSwap`/`validMint`/`validRedeem` check `INPUTS(1)`, `validIntervention` checks `INPUTS(2)`,
   `validExtraction` checks `INPUTS(1)`), and that OUTPUTS(0) preserves the script and the token **IDs**
   (`preservedLpTokenId`, `preservedDexyTokenId` compare only `_1`, i.e. the token id, not the quantity). It
   never asserts `INPUTS(0) == SELF`. Consequently the genuine LP box can be placed at any input index and
   still be spendable as long as a valid action box sits at the index main.es expects.

Notably, `contracts/lp/pool/extract.es:106` already contains the missing check
(`lpBoxIn.tokens(0)._1 == lpNFT`, next to the comment "Maybe this check not needed? (see LP box)"). The hack
demonstrates that the check was needed, in the action contract, precisely because main.es does not enforce the
position of the pool box.


Exploit mechanics
-----------------

A draining transaction has the following shape:

    Input         | Output         | Data-Input
    --------------+----------------+-----------
    0 Fake LP box | 0 "LP" out     |
    1 Swap box    | 1 Swap box    |
    2 Real LP box | 2 Hacker out  |
    ...           | 3 Hacker out  |

* **INPUTS(0)** is a fake "LP" box created by the attacker: a box paying to a trivially-true script (e.g.
  `sigmaProp(true)`), holding dust Ergs and arbitrary tokens (including the tokens minted in the preparation
  step, so that `tokens(1)`/`tokens(2)` accesses succeed). Its script imposes no conditions, so the attacker
  controls its successor entirely.
* **INPUTS(1)** is the genuine swap action box, spent and preserved at OUTPUTS(1) as the contract requires
  (`selfPreserved`, swap.es:80-82). This satisfies main.es's `validSwap` (`INPUTS(1).tokens(0)._1 == swapNFT`).
* **INPUTS(2)** is the **real LP box**. Its script (main.es) evaluates against OUTPUTS(0): the LP NFT is
  preserved, the LP-token and Dexy token *ids* are preserved, the script is preserved, there are exactly three
  tokens — but the Ergs value and the Dexy quantity on OUTPUTS(0) can be slashed almost to zero, because main.es
  never checks them (quantities and value were supposed to be guarded by the action contract — which is looking
  at the fake box instead). The main.es condition `(lpAction || dexyAction)` holds via `validSwap`.
* **swap.es** evaluates its invariant between the fake INPUTS(0) and fake OUTPUTS(0); with zero (or freely
  crafted) deltas the inequality passes, and the swap box is preserved.
* The difference between the real LP box's reserves and the stripped OUTPUTS(0) is paid to attacker outputs
  (exchanges' deposit addresses).

Both LP deployments (USE and DexyGold) used the same templates, so the same transaction shape drained the second
pool immediately after the first.


Audit of the remaining contracts
--------------------------------

All other protocol contracts were reviewed for the same vulnerability class (counterpart boxes referenced by
position without an identity check):

* `contracts/lp/pool/extract.es` — **OK.** Checks `lpBoxIn.tokens(0)._1 == lpNFT` (line 106) and the NFT
  preservation on the pool output (line 107).
* `contracts/bank/intervention.es` — **OK.** Checks `lpNFT` and `bankNFT` on the pool and bank inputs
  (lines 93-94); oracle and tracking boxes are NFT-checked data inputs.
* `contracts/bank/arbmint.es`, `contracts/bank/freemint.es` — **OK.** The LP box is a *data input* checked by
  `lpNFT` (arbmint.es:123, freemint.es:108); bank and buyback boxes are NFT-checked inputs.
* `contracts/tracking.es` — **OK.** The LP box is an NFT-checked data input (line 51).
* `contracts/lp/proxy/*` — **OK.** User-facing proxies check the pool NFT, e.g. `SwapSellV1.es:24`
  (`validPoolIn = poolNFT == fromBase64("$lpNFT")`).
* `contracts/bank/bank.es` — **OK** in combination with the above: action boxes are bound by NFT at fixed
  positions (lines 75-80), and every action contract in turn NFT-checks the bank box, so a fake bank box cannot
  be substituted.
* `contracts/bank/payout.es` — **soft spot (not exploited, hardening recommended).** The bank box at
  INPUTS(1) is bound only by full-token-tuple equality (`bankBoxOut.tokens == bankBoxIn.tokens`, lines 72-74)
  rather than by bank NFT + script. This is not exploitable for profit on its own (the payment is taken solely
  from the very box being validated), but binding by identity removes an unnecessary reliance on token-tuple
  equality and aligns payout with the other action contracts.


Recommended fixes (APPLIED in the afterhack branch, September 2026)
------------------------------------------------------------------

The changes below are applied to the contract templates, with regression tests in
`src/test/scala/dexy/lp/LpHackSpec.scala` that replay the exploit shape (fake LP box at INPUTS(0), real
LP box at the unexpected INPUTS(2) position, reserve-stripped LP successor at OUTPUTS(0)). Verified
against a pre-fix worktree: the exploit transactions succeed without the fixes and are rejected with
them.

1. `contracts/lp/pool/swap.es`, `contracts/lp/pool/mint.es`, `contracts/lp/pool/redeem.es`:
   add the LP NFT check on the pool input and output, mirroring `extract.es`:

       val lpNFT = fromBase64("$lpNFT") // placeholder already available in the substitution maps
       val validLpBox = lpBoxIn.tokens(0)._1 == lpNFT &&
                        lpBoxOut.tokens(0)._1 == lpNFT

   and conjunct `validLpBox` into the final `sigmaProp`.

2. `contracts/lp/pool/main.es`: bind the pool box to its expected position so that the "real LP box in an
   unexpected position" half of the exploit is closed for every action (swap/mint/redeem/intervention/extract):

       val validPosition = INPUTS(0).id == SELF.id // LP box must be the first input

   conjuncted into the final `sigmaProp`. (Checking `INPUTS(0).tokens(0) == SELF.tokens(0)` is an equivalent
   alternative, since the LP NFT is unique.)

3. `contracts/bank/payout.es` (defense-in-depth): replace the token-tuple-only binding with an NFT check,

       val bankNFT = fromBase64("$bankNFT")
       ... bankBoxIn.tokens(0)._1 == bankNFT ...

   and require the bank successor to preserve the bank script.

4. Regression tests (to be added with the fix, mirroring the style of `src/test/scala/dexy/lp/LpSwapSpec`):
   for each of swap/mint/redeem, a negative property that builds the exploit transaction — fake LP box
   (`fakeScript` from `Common.scala`, no lpNFT) at INPUTS(0), the real action box at INPUTS(1), the real LP box
   at INPUTS(2), a reserve-stripped LP successor at OUTPUTS(0) — and asserts `the[Exception] thrownBy
   { TxUtil.createTx(...) }`. The equivalent transaction must fail after the fix while all existing positive
   tests keep passing.

5. Contract headers: when the contracts are edited, update their header transaction tables/comments to document
   the new checks, per the repository convention.


Deployment impact
-----------------

Smart contracts on Ergo are immutable: the pools deployed on mainnet cannot be patched in place. The deployed
USE and DexyGold LP sets (pool box plus swap/mint/redeem/extract action boxes) remain vulnerable to this
exploit until the protocol is re-deployed from the (now fixed) templates. A re-deployment implies issuing a new LP NFT and
new action NFTs and re-anchoring every contract that refers to them (`$lpNFT`, `$lpSwapNFT`, `$lpMintNFT`,
`$lpRedeemNFT`, `$extractionNFT` — see the token-id lists in `spec/deployment-usd.md` and
`spec/deployment-gold.md`, and the `*TokenIds` objects in `src/main/scala/dexy/chainutils/`). Until such a
re-deployment, any liquidity held behind the current LP contracts should be considered at risk.


References
----------

[1] USE & DexyGold LP hack postmortem, Ergo Forum, September 2026.
    https://ergoforum.org/t/use-dexygold-lp-hack-postmortem/5362

[2] Funding transaction: https://explorer.ergoplatform.com/en/transactions/f0d551d886072ce3c75da203c10b4c7a01f8ad6b6cb836e15f656d648fe50325
