{

    // ===== Contract Information ===== //
    // Name: Phoenix HodlCoin Bank
    // Description: Contract for the bank box of the HodlCoin protocol.
    // Version: 1.0.0
    // Author: Luca D'Angelo (ldgaetano@protonmail.com), MGPai

    // ===== Box Contents ===== //
    // Tokens
    // 1. (BankSingletonId, 1)
    // 2. (HodlCoinTokenId, HodlCoinTokenAmount)

    // ===== Relevant Transactions ===== //
    // 1. Mint Tx
    // Inputs: Bank, Proxy
    // DataInputs: None
    // Outputs: Bank, UserPK, MinerFee, TxOperatorFee
    // Context Variables: None
    // 2. Burn Tx
    // Inputs: Bank, Proxy
    // DataInputs: None
    // Outputs: Bank, UserPK, PhoenixFee, MinerFee, TxOperatorFee
    // Context Variables: None

    // ===== Compile Time Constants ($) ===== //
    // $phoenixFeeContractBytes: Coll[Byte]

    // ===== Context Variables (@) ===== //
    // None

    // ===== Relevant Variables ===== //
    val totalTokenSupply: Long = 97739924000000000L // Same as ERG total supply
    val bankFee: (Long, Long) = (3L, 100L)
    val devFee: (Long, Long) = (5L, 100L)
    val precisionFactor: Long = 1000000000L

    // Bank Input
    val reserveIn: Long = SELF.value
    val hodlCoinsIn: Long = SELF.tokens(1)._2                   // hodlERG token amount in the bank box.
    val hodlCoinsCircIn: Long = totalTokenSupply - hodlCoinsIn  // hodlERG in circulation since this value represents what is not inside the box, this must not ever be 0.

    // Bank Output
    val bankBoxOUT: Box = OUTPUTS(0)
    val reserveOut: Long = bankBoxOUT.value
    val hodlCoinsOut: Long = bankBoxOUT.tokens(1)._2

    // Bank Info
    val hodlCoinsCircDelta: Long = hodlCoinsIn - hodlCoinsOut  // When minting hodlCoin, this is the amount of coins the user gets.
    val price: Long = (reserveIn * precisionFactor) / hodlCoinsCircIn
    val isMintTx: Boolean = (hodlCoinsCircDelta > 0L)

    // Inputs
    val proxyBoxIN: Box = INPUTS(0)
    val userPK: SigmaProp = proxyBoxIN.R4[SigmaProp].get
    val minerFee: Long = proxyBoxIN.R5[Long].get
    val minBoxValue: Long = proxyBoxIN.R6[Long].get
    val minTxOperatorFee: Long = proxyBoxIN.R7[Long].get

    // Outputs
    val userPKBoxOUT: Box = OUTPUTS(1)

    val validBankRecreation: Boolean = {

        val validValue: Boolean = (bankBoxOUT.value >= 1000000000L) // There must be at least 1 ERG always in the box

        val validContract: Boolean = (bankBoxOUT.propositionBytes == SELF.propositionBytes)

        val validTokens: Boolean = {

            val validBankSingleton: Boolean = (bankBoxOUT.tokens(0) == SELF.tokens(0))          // Singleton token amount never changes
            val validHodlCoinTokenId: Boolean = (bankBoxOUT.tokens(1)._1 == SELF.tokens(1)._1)
            val validHodlCoinTokenAmount: Boolean = (bankBoxOUT.tokens(1)._2 >= 1L)             // HodlCoin token amount can change, but there must be 1 hodlerg inside the bank always

            allOf(Coll(
                validBankSingleton,
                validHodlCoinTokenId,
                validHodlCoinTokenAmount
            ))

        }

        allOf(Coll(
            validValue,
            validContract,
            validTokens
        ))

    }

    if (isMintTx) {

        // ===== Mint Tx ===== //
        val validMintTx: Boolean = {

            // Outputs
            val minerFeeBoxOUT: Box = OUTPUTS(2)
            val txOperatorBoxOUT: Box = OUTPUTS(3)

            // Mint info
            val expectedAmountDeposited: Long = (hodlCoinsCircDelta * price) / precisionFactor // Price of hodlCoin in nanoERG.

            val validBankDeposit: Boolean = (reserveOut == reserveIn + expectedAmountDeposited)

            val validProxyValue: Boolean = (proxyBoxIN.value - minBoxValue - minerFee - minTxOperatorFee >= expectedAmountDeposited) // The proxy box must have enough to cover the min-fee to create the user pk output that holds the hodlCoins, the miner-fee, and the min tx-operator fee.

            val validUserBoxOUT: Boolean = {

                val validValue: Boolean = (userPKBoxOUT.value == minBoxValue)
                val validContract: Boolean = (userPKBoxOUT.propositionBytes == userPK.propBytes)
                val validHodlCoinTransfer: Boolean = (userPKBoxOUT.tokens(0) == (SELF.tokens(1)._1, hodlCoinsCircDelta))

                allOf(Coll(
                    validValue,
                    validContract,
                    validHodlCoinTransfer
                ))

            }

            val validMinerFee: Boolean = (minerFeeBoxOUT.value == minerFee)

            val validTxOperatorFee: Boolean = (txOperatorBoxOUT.value >= minTxOperatorFee)

            val validOutputSize: Boolean = (OUTPUTS.size == 4)

            allOf(Coll(
                validBankRecreation,
                validBankDeposit,
                validProxyValue,
                validUserBoxOUT,
                validMinerFee,
                validTxOperatorFee,
                validOutputSize
            ))

        }

        sigmaProp(validMintTx)

    } else {

        // ===== Burn Tx ===== //
        val validBurnTx: Boolean = {

            // Outputs
            val phoenixFeeBoxOUT: Box = OUTPUTS(2)
            val minerFeeBoxOUT: Box = OUTPUTS(3)
            val txOperatorBoxOUT: Box = OUTPUTS(4)

            val hodlCoinsBurned: Long = hodlCoinsOut - hodlCoinsIn
            val expectedAmountBeforeFees: Long = (hodlCoinsBurned * price) / precisionFactor
            val bankFeeAmount: Long = (expectedAmountBeforeFees * bankFee._1) / (bankFee._2 * precisionFactor)
            val devFeeAmount: Long = (expectedAmountBeforeFees * devFee._1) / (devFee._2 * precisionFactor)
            val expectedAmountWithdrawn: Long = expectedAmountBeforeFees - bankFeeAmount - devFeeAmount // The user never gets the bankFeeAmount since it remains in the bank box, the devFeeAmount is the only ERG that leaves the box.

            val validBurn: Boolean = (proxyBoxIN.tokens(0) == (SELF.tokens(1)._1, hodlCoinsBurned))

            val validBankWithdraw: Boolean = (reserveOut == reserveIn - (expectedAmountBeforeFees - devFeeAmount))

            val validUserBoxOUT: Boolean = {

                val validERGTransfer: Boolean = (userPKBoxOUT.value == expectedAmountWithdrawn)
                val validContract: Boolean = (userPKBoxOUT.propositionBytes == userPK.propBytes)

                allOf(Coll(
                    validERGTransfer,
                    validContract
                ))

            }

            val validPhoenixFee: Boolean = {

                allOf(Coll(
                    (phoenixFeeBoxOUT.value == devFeeAmount),
                    (phoenixFeeBoxOUT.propositionBytes == $phoenixFeeContractBytes) // The phoenix fee contract distributes the rewards to the three original developers and the Phoenix Finance developers.
                ))

            }

            val validMinerFee: Boolean = (minerFeeBoxOUT.value == minerFee)

            val validTxOperatorFee: Boolean = (txOperatorBoxOUT.value >= minTxOperatorFee)

            val validOutputSize: Boolean = (OUTPUTS.size == 5)

            allOf(Coll(
                validBankRecreation,
                validBurn,
                validBankWithdraw,
                validUserBoxOUT,
                validPhoenixFee,
                validMinerFee,
                validTxOperatorFee,
                validOutputSize
            ))

        }

        sigmaProp(validBurnTx)

    }

}