Bank Recovery Runbook: sweeping the USE and DexyGold banks to a trusted address
=================================================================================

Context: on 2026-09-07 the USE and DexyGold LPs were drained via the vulnerability documented in
`spec/postmortem-lp-hack.md`. The bank boxes still hold protocol reserves (USE bank ~292,615 ERG + the unused
USE supply; DexyGold bank ~4,760 ERG + the unused DexyGold supply) and must be swept to a trusted address
before any further damage. This runbook uses the bank contract's update path to do it in one transaction per
bank (two transactions for DexyGold: the carrier preparation in step 1, then the drain in step 2).

Mechanism
---------

`contracts/bank/bank.es` ends with `sigmaProp((validSuccessor && (...)) || validUpdate)`, where
`validUpdate = INPUTS(0).tokens(0)._1 == updateNFT`. If the first input carries the deployment's update NFT
at tokens(0), every other bank condition is bypassed: the bank box can be spent to ANY script with ANY value.
No `update.es`/ballot machinery is involved — `bank.es` only checks the NFT id, so the update NFT can sit in
any box the operator controls (a "carrier"; both deployments keep theirs in operator P2PK boxes).

Preconditions
-------------

1. A synced mainnet Ergo node with `ergo { extraIndex = true }` (all box reads use the node's
   `/blockchain` extra-index API; `serverUrl` in `OffchainUtils`/`BankRecovery`).
2. The node wallet holds the private keys of:
   * the USE update carrier, P2PK `9iQJg8pmqP9G5sUcbtV9AWQQnBs52gdeKb2ktZjV6E69y3firSU`
     (box `1ee201520f4353b3d619ccbca8c5432f1ab2fa855db858bfca5f56cf989f4688`), and
   * the DexyGold treasury box `3b02f44febdb3b858dff84a4f2580841bcdab1ec768f409771569741bc5cb63c`,
   and is unlocked while signing (`POST /wallet/unlock`). The recovery tool itself handles no secrets:
   it builds the unsigned transaction and writes a `/wallet/transaction/sign` request; the node wallet
   signs the carrier/treasury input (the bank input needs no signature and gets an empty proof).
3. The tool spends the deployed, immutable bank boxes and never reads the contract sources in
   `contracts/`, so contract template changes merged in this repo do not affect the sweep. The
   carrier path also survives an on-chain bank update to the current template (its
   `INPUTS(1).id == SELF.id` binding is satisfied by the drain shape — see
   `BankRecoveryContractSpec`); it would break only if a future update removes the carrier path,
   e.g. by pinning INPUTS(0) to the `update.es` script hash.
4. `sbt compile` passes (see "Build notes" below).

Snapshot of the relevant boxes (2026-09-08, height ~1868825 — RE-VERIFY before executing; the bank box
changes on every protocol action):

* USE bank:      `e6162e2aff23f7c88968cc958541bacfe3ad80d6541befb7231ac3106e966f8b` (bankNFT `78c24bdf…75ae`)
* USE carrier:   `1ee201520f4353b3d619ccbca8c5432f1ab2fa855db858bfca5f56cf989f4688` (updateNFT `f77b3cac…e883` at tokens(0))
* Gold bank:     `fdcf983e9d6bf1f23429419c2e4dc1b858fddf6c05dd1f7f4e77e2be07b0c616` (bankNFT `75d7bfbf…9f7f`)
* Gold treasury: `3b02f44febdb3b858dff84a4f2580841bcdab1ec768f409771569741bc5cb63c` (updateNFT `7a776cf7…bf09` NOT at tokens(0))

Verify freshness:

```bash
# against the operator node ($NODE = serverUrl in OffchainUtils)
curl -s $NODE/blockchain/box/unspent/byTokenId/78c24bdf41283f45208664cd8eb78e2ffa7fbb29f26ebb43e6b31a46b3b975ae | python3 -m json.tool | grep boxId
curl -s $NODE/blockchain/box/unspent/byTokenId/75d7bfbfa6d165bfda1bad3e3fda891e67ccdcfc7b4410c1790923de2ccc9f7f | python3 -m json.tool | grep boxId
```

If either bank box id differs from the snapshot, use the fresh id in the commands below.

Procedure
---------

Do it in one session; re-fetch ids before every step. Each run of the tool only BUILDS a transaction: it
fetches the input boxes, validates them (bank identified by its bankNFT, carrier must hold the update NFT
at tokens(0), neither box may be spent), prints the inputs, outputs and the unsigned tx id for review, and
writes `<name>-sign-request.json` — a ready-to-post body for `/wallet/transaction/sign`. The unsigned tx
is built at the maximum creation height of its inputs, so re-running with the same unspent boxes produces
an identical request file. Sign and submit each step before moving to the next:

```bash
# sign (node wallet must hold the carrier key and be unlocked)
curl -X POST -H "api_key: $API_KEY" -H "Content-Type: application/json" -d @<name>-sign-request.json $NODE/wallet/transaction/sign > <name>-signed.json
# review the signed transaction in <name>-signed.json, then submit
curl -X POST -H "api_key: $API_KEY" -H "Content-Type: application/json" -d @<name>-signed.json $NODE/transactions
```

1. **prep-gold** — carve a carrier out of the DexyGold treasury (update NFT moved to tokens(0), everything
   else returns to the treasury P2PK):

   ```bash
   sbt "runMain offchain.BankRecovery prep-gold"
   # sign + submit prep-gold-sign-request.json as above
   ```

   Record the new carrier box id from the transaction outputs
   (`curl -s $NODE/blockchain/box/unspent/byTokenId/7a776cf75b8b3a5aac50a36c41531a4d6f1e469d2cbcaa5795a4f5b4c255bf09`).

2. **drain DexyGold bank** (the gold carrier is under the treasury key, so the same node wallet signs):

   ```bash
   sbt "runMain offchain.BankRecovery drain fdcf983e9d6bf1f23429419c2e4dc1b858fddf6c05dd1f7f4e77e2be07b0c616 <carrierBoxId> <trustedAddress> 7a776cf75b8b3a5aac50a36c41531a4d6f1e469d2cbcaa5795a4f5b4c255bf09 75d7bfbfa6d165bfda1bad3e3fda891e67ccdcfc7b4410c1790923de2ccc9f7f"
   # sign + submit drain-sign-request.json as above
   ```

3. **drain USE bank** (the big one — ~292,615 ERG; move or rename the gold `drain-sign-request.json`
   first, it is overwritten):

   ```bash
   sbt "runMain offchain.BankRecovery drain e6162e2aff23f7c88968cc958541bacfe3ad80d6541befb7231ac3106e966f8b 1ee201520f4353b3d619ccbca8c5432f1ab2fa855db858bfca5f56cf989f4688 <trustedAddress> f77b3cac4f77a31aeffaf716070345b3b04330bbba02e27671015129fb74e883 78c24bdf41283f45208664cd8eb78e2ffa7fbb29f26ebb43e6b31a46b3b975ae"
   # sign + submit drain-sign-request.json as above
   ```

Transaction shape (drain): INPUTS(0) = carrier (update NFT at tokens(0), signed by the node wallet),
INPUTS(1) = bank box (bank script passes via validUpdate, empty proof); OUTPUTS(0) = trusted box with
bankNFT ×1 + all bank tokens + (bank value − 0.002 ERG fee), OUTPUTS(1) = carrier change, fee box.

Safety notes
------------

* The drain transaction needs ONLY the carrier key's signature; the node wallet's signature commits to all
  inputs and outputs, so the signed transaction cannot be redirected in the mempool. However, a competing
  spend of the bank box (through the bank's normal action contracts) or of the carrier box cancels it —
  the tool then refuses to build (box already spent, per the node extra index) and the transaction must be
  rebuilt with the fresh box id.
* The tool never signs and never broadcasts: review the printed inputs/outputs and the unsigned tx id,
  and review the signed transaction returned by the node before submitting it.
* Miner fee is 0.002 ERG per transaction.

Verification (after each broadcast)
-----------------------------------

* `curl -s https://api.ergoplatform.com/api/v1/addresses/<trustedAddress>` — balance increased by the bank
  value minus fee, and the box list contains the bankNFT and the Dexy/USE tokens.
* The old bank boxes report `"spentTransactionId": "..."` when queried via
  `https://api.ergoplatform.com/api/v1/boxes/<boxId>`.
* Optionally cross-check on `https://explorer.ergoplatform.com`.

Afterwards
----------

* Add a line to `spec/deployment-usd.md` / `spec/deployment-gold.md`: "bank swept to <trusted address> in tx
  <id> on 2026-09-XX; protocol paused".
* The protocol's remaining boxes (tracking, action, buyback boxes) hold no bank reserves; sweeping them is
  optional and low priority. Do NOT redeploy or reuse the current contract templates — they contain the
  vulnerability described in `spec/postmortem-lp-hack.md`.

Build notes (why the dependencies changed)
------------------------------------------

* `kiosk` 1.0.2 and `ergo-core` 5.0.20 disappeared from public repositories (old Sonatype OSSRH shutdown).
  Kiosk is now `1.0.0` published locally from the master of `github.com/ergoplatform/kiosk`, with two
  dexy-specific patches the test suite requires (the `KioskBoolean` type in `ergo/package.scala`, and a
  `creationHeight: Option[Int]` passthrough on `KioskBox`/`TxUtil.createTx` in `tx/TxUtil.scala`); a
  `dependencyOverrides` forces the identical `scrypto` 2.3.0 release in place of the purged snapshot, both
  here and in kiosk's build. `ergo-core` was dropped: the `offchain` package now builds on types from
  `sigma-state`/`ergo-wallet` (both on Maven Central).
