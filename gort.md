Gort tokenomics
===============


Motivation
----------

In Ergo, there are no calls from one contract to another, composability is done differently, via a transaction possibly using multiple input
boxes with different (composable) contracts. And as there are read-only inputs here, oracle pool is delivering data in a box to be only read.
This is very efficient, but does not allow for charging for using oracle's data. However, applications are interested in maintaining security of oracles.

In this proposal, we provide a solution for rewarding oracles in a trustless way. Hopefully, the proposal could be
useful for other applications as well.


Oracle Pool And Its Token
-------------------------

In oracle pool 2.0 [1] oracles are rewarded with custom tokens. For gold oracle, we call this token GORT (Gold Oracle Reward Token). 

Oracle pool is consensus protocol on data, where time is divided into epochs, and at the end of each epoch updated data point is delivered. 
On delivering new data point, the oracle pool 2.0 contract [2] is rewarding oracles with 1 token, except for an oracle assembling a rewarding transaction, which is getting n tokens, where n is number of oracles getting 1 token. Thus if there are N active oracles in epoch, oracle pool contract is spending 2*(N-1) tokens per epoch.

For simplicity, we embed constants into our model. For gold oracle, epoch is about 1 hour. Assuming that oracle pool will have 30 operators, we are getting up to 60 tokens released per 1 hour, and we build tokenomics on top of that.


Gold Cooperative
----------------

Gold Cooperative (GC) consists of development and marketing teams of Dexy and Gluon stablecoins. Initial deployments for both the stablecoins will be pegged to gold, thus using the gold oracle pool data. For both protocols, it is important to reward the oracle pool, as for both protocols the oracle pool is the only trusted part.

Thus for every mint in Dexy bank, in addition to bank fee which is going into back reserves, there's 0.2% fee going to buyback contract [2]. If
Dexy bank hass too much reserves (overcollateralization above 1,000%), the bank is sending GORTs to the buyback contract why reserves are abve the threshold. Buyback contract is then buying GORTs from GORT/ERG LP with fees coming in form of ERGs and sending GORTs bought back to the oracle pool contract.

Thus there are few feedback loops here:

* oracle operators are dumping ERGs received from the oracle pool contract in the LP, and buyback contract is buying them from the LP. GORT price is then defined by these supply and demand factors. There could be other players in the LP, for examples, speculators may come after big dumps but before buybacks to make quick profit, in case of visible or expected long-time trends investors can participate in the game also.

* if GORT pice is too low at some point, some oracle pool operators may shut down their business, maybe even selling operator tokens (as oracle operator token is transferrable in 2.0). However, with reduced number of active operators there's less emission of new GORTs, with the same demand from the buyback contract, means some price recovery. 

Also, there's need for Gold Cooperative (GC) to pay the bills (for man-hours, servers and other infrastructure etc). Another ways of rewarding GC 
could be used, such as separate protocol-level fee, UI-level fee, and so on, but we are going to propose to reward developers with GORTs as well, to have interests of oracle pool operators and developers aligned.

We propose to launch emission contract which is releasing about 60 GORTs per one hour (1 GORT per block) as well for bootstrapping period of 2 years. GORTs are going to GC DAO. 


Tokenomics
----------

Summarizing tokenomics details from above: 
* up to 60 GORTs per hour are released to oracle pool operators
* for first two years, 60 GORTs per block also going to Gold Cooperative, developers behind DexyGold and (Gluon-Based) SigGold
* Dexy and Gluon (and maybe more gold-related products in future) are sending 0.2% of fees to buyback contract which is buying back GORTs from GORT/ERG LP and sending them to oracle pool contract 


References
----------

[1] Oracle pool 2.0 specification https://github.com/ergoplatform/eips/pull/41

[2] Buying back tokens from liqudity pool https://www.ergoforum.org/t/buying-back-tokens-from-liqudity-pool/4275
