{

    // ===== Contract Information ===== //
    // Name: Phoenix HodlCoin Fee
    // Description: Contract guarding the fee box for the HodlCoin protocol.
    // Version: 1.0.0
    // Author: Luca D'Angelo (ldgaetano@protonmail.com), MGPai

    // ===== Box Contents ===== //
    // Tokens
    // None
    // Registers
    // None

    // ===== Relevant Transactions ===== //
    // 1. Fee Distribution Tx
    // Inputs: PhoenixFee1, ... , PhoenixFeeM
    // DataInputs: None
    // Outputs: Dev1PK, Dev2PK, Dev3PK, PhoenixPK
    // Context Variables: None

    // ===== Compile Time Constants ($) ===== //
    // $devPercentage: (Long, Long)
    // $phoenixPercentage: (Long, Long)
    // $minerFee: Long

    // ===== Context Variables (@) ===== //
    // None

    // ===== Relevant Variables ===== //
    val dev1Address: SigmaProp              = PK("9hHondX3uZMY2wQsXuCGjbgZUqunQyZCNNuwGu6rL7AJC8dhRGa")
    val dev2Address: SigmaProp              = PK("9gnBtmSRBMaNTkLQUABoAqmU2wzn27hgqVvezAC9SU1VqFKZCp8")
    val dev3Address: SigmaProp              = PK("9iE2MadGSrn1ivHmRZJWRxzHffuAk6bPmEv6uJmPHuadBY8td5u")
    val phoenixAddress: SigmaProp           = PK("9iPs1ujGj2eKXVg82aGyAtUtQZQWxFaki48KFixoaNmUAoTY6wV")
    val minerFeeErgoTreeBytes: Coll[Byte]   = fromBase64("$minerTree")

    // ===== Fee Distribution Tx ===== //
    val validFeeDistributionTx: Boolean = {

        // Outputs
        val dev1BoxOUT: Box     = OUTPUTS(0)
        val dev2BoxOUT: Box     = OUTPUTS(1)
        val dev3BoxOUT: Box     = OUTPUTS(2)
        val phoenixBoxOUT: Box  = OUTPUTS(3)
        val minerFeeBoxOUT: Box = OUTPUTS(4)

        val outputAmount: Long = OUTPUTS.map({ (output: Box) => output.value }).fold(0L, { (acc: Long, curr: Long) => acc + curr })
        val devAmount: Long = outputAmount - minerFeeBoxOUT.value // In case the miner fee increases in the future.

        val validPercentages: Boolean = {

            (devPercentage._1 * phoenixPercentage._2 + phoenixPercentage._1 * devPercentage._2) == (devPercentage._2 * phoenixPercentage._2) // (a/b + c/d = 1 => ad + cb = bd)

        }

        val validDevBoxes: Boolean = {

            val devAllocation: Long = ((devPercentage._1 * devAmount) / devPercentage._2) / 3L

            allOf(Coll(
                (dev1BoxOUT.value == devAllocation),
                (dev1BoxOUT.propositionBytes == dev1Address.propBytes),
                (dev2BoxOUT.value == devAllocation),
                (dev2BoxOUT.propositionBytes == dev2Address.propBytes),
                (dev3BoxOUT.value == devAllocation),
                (dev3BoxOUT.propositionBytes == dev3Address.propBytes)
            ))

        }

        val validPhoenixBox: Boolean = {

            allOf(Coll(
                (phoenixBoxOUT.value == (phoenixPercentage._1 * devAmount) / phoenixPercentage._2),
                (phoenixBoxOUT.propositionBytes == phoenixAddress.propBytes)
            ))

        }

        val validMinerFee: Boolean = {

            allOf(Coll(
                (minerFeeBoxOUT.value >= $minerFee), // In case the miner fee increases in the future
                (minerFeeBoxOUT.propositionBytes == minerFeeErgoTreeBytes)
            ))

        }

        val validOutputSize: Boolean = (OUTPUTS.size == 5)

        allOf(Coll(
            validPercentages,
            validDevBoxes,
            validPhoenixBox,
            validMinerFee,
            validOutputSize
        ))

    }

    sigmaProp(validFeeDistributionTx) && atLeast(1, Coll(dev1Address, dev2Address, dev3Address, phoenixAddress))

}