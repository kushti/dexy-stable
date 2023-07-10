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
    // Registers
    // R4: Long             TotalTokenSupply
    // R5: Long             PrecisionFactor
    // R6: Long             MinBankValue
    // R7: Long            BankFee
    // R8: Long            DevFee

    // ===== Relevant Transactions ===== //
    // 1. Mint Tx
    // Inputs: Bank, Proxy
    // Data Inputs: None
    // Outputs: Bank, BuyerPK, MinerFee, TxOperatorFee
    // Context Variables: None
    // 2. Burn Tx
    // Inputs: Bank, Proxy
    // Data Inputs: None
    // Outputs: Bank, BuyerPK, PhoenixFee, MinerFee, TxOperatorFee
    // Context Variables: None

    // ===== Compile Time Constants ($) ===== //
    // $phoenixFeeContractBytes: Coll[Byte]

    // ===== Context Variables (@) ===== //
    // None

    // ===== Relevant Variables ===== //
    val totalTokenSupply: Long      = SELF.R4[Long].get
    val precisionFactor: Long       = SELF.R5[Long].get
    val minBankValue: Long          = SELF.R6[Long].get
    val devFee: Long                = SELF.R7[Long].get
    val bankFee: Long               = SELF.R8[Long].get
    val feeDenom                    = 1000L

    // Bank Input
    val reserveIn: Long         = SELF.value
    val hodlCoinsIn: Long       = SELF.tokens(1)._2               // hodlERG token amount in the bank box.
    val hodlCoinsCircIn: Long   = totalTokenSupply - hodlCoinsIn  // hodlERG in circulation since this value represents what is not inside the box, this must not ever be 0.

    // Bank Output
    val bankBoxOUT: Box     = OUTPUTS(0)
    val reserveOut: Long    = bankBoxOUT.value
    val hodlCoinsOut: Long  = bankBoxOUT.tokens(1)._2

    // Bank Info
    val hodlCoinsCircDelta: Long    = hodlCoinsIn - hodlCoinsOut // When minting hodlCoin, this is the amount of coins the buyer gets.
    val price: Long                 = (reserveIn.toBigInt * precisionFactor) / hodlCoinsCircIn
    val isMintTx: Boolean           = (hodlCoinsCircDelta > 0L)

    val validBankRecreation: Boolean = {

        val validValue: Boolean = (bankBoxOUT.value >= minBankValue) // There must be at least 1 ERG always in the box

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

        val validRegisters: Boolean = {

            allOf(Coll(
                (bankBoxOUT.R4[Long].get == SELF.R4[Long].get),
                (bankBoxOUT.R5[Long].get == SELF.R5[Long].get),
                (bankBoxOUT.R6[Long].get == SELF.R6[Long].get),
                (bankBoxOUT.R7[Long].get == SELF.R7[Long].get),
                (bankBoxOUT.R8[Long].get == SELF.R8[Long].get)
            ))

        }

        allOf(Coll(
            validValue,
            validContract,
            validTokens,
            validRegisters
        ))

    }

    if (isMintTx) {

        // ===== Mint Tx ===== //
        val validMintTx: Boolean = {

            val expectedAmountDeposited: Long = (hodlCoinsCircDelta * price) / precisionFactor // Price of hodlCoin in nanoERG.

            val validBankDeposit: Boolean = (reserveOut >= reserveIn + expectedAmountDeposited)

            allOf(Coll(
                validBankRecreation,
                validBankDeposit
            ))

        }
        sigmaProp(validMintTx)
    } else {

        // ===== Burn Tx ===== //
        val validBurnTx: Boolean = {

            val hodlCoinsBurned: Long = hodlCoinsOut - hodlCoinsIn
            val expectedAmountBeforeFees: Long = (hodlCoinsBurned * price) / precisionFactor
            val bankFeeAmount: Long = (expectedAmountBeforeFees * bankFee) / feeDenom
            val devFeeAmount: Long = (expectedAmountBeforeFees * devFee) / feeDenom
            val expectedAmountWithdrawn: Long = expectedAmountBeforeFees - bankFeeAmount - devFeeAmount // The buyer never gets the bankFeeAmount since it remains in the bank box, the devFeeAmount is the only ERG that leaves the box.

            val validBankWithdraw: Boolean = (reserveOut == reserveIn - (expectedAmountBeforeFees - devFeeAmount))

            val validPhoenixFee: Boolean = {
                val phoenixFeeBoxOUT = OUTPUTS(2)
                allOf(Coll(
                    phoenixFeeBoxOUT.value == devFeeAmount,
                    blake2b256(phoenixFeeBoxOUT.propositionBytes) == fromBase64("$phoenixFeeContractBytesHash")
                ))

            }

            allOf(Coll(
                validBankRecreation,
                validBankWithdraw
            ))

        }

        sigmaProp(validBurnTx)

    }

}