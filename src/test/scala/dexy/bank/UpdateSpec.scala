package dexy.bank

import dexy.Common
import dexy.chainutils.UseSpec.{ballotAddress, bankAddress, bankErgoTree, bankScript, bankUpdateAddress, payoutAddress}
import org.ergoplatform.kiosk.ErgoUtil
import org.ergoplatform.kiosk.encoding.ScalaErgoConverters
import org.ergoplatform.kiosk.encoding.ScalaErgoConverters.stringToGroupElement
import org.ergoplatform.kiosk.ergo.{DhtData, KioskBox, KioskCollByte, KioskGroupElement, KioskInt}
import org.ergoplatform.kiosk.tx.TxUtil
import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ContextVar, HttpClientTesting}
import org.ergoplatform.sdk.ErgoToken
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scorex.crypto.hash.Blake2b256

class UpdateSpec extends PropSpec with Matchers with ScalaCheckDrivenPropertyChecks
  with HttpClientTesting with Common {

  import dexy.chainutils.MainnetUseTokenIds._

  val ergoClient = createMockedErgoClient(MockData(Nil, Nil))

  val fakeNanoErgs = 10000000000000L

  val fee = 1500000

  // update tests for extraction and intervention actions are in corresponding specs!

  property("Bank update should work") {
    ergoClient.execute { implicit ctx: BlockchainContext =>
      object Voters {
        // define voters
        val addresses = Seq(
          "9eiuh5bJtw9oWDVcfJnwTm1EHfK5949MEm5DStc2sD1TLwDSrpx", // private key is 37cc5cb5b54f98f92faef749a53b5ce4e9921890d9fb902b4456957d50791bd0
          "9f9q6Hs7vXZSQwhbrptQZLkTx15ApjbEkQwWXJqD2NpaouiigJQ", // private key is 5878ae48fe2d26aa999ed44437cffd2d4ba1543788cff48d490419aef7fc149d
          "9fGp73EsRQMpFC7xaYD5JFy2abZeKCUffhDBNbQVtBtQyw61Vym", // private key is 3ffaffa96b2fd6542914d3953d05256cd505d4beb6174a2601a4e014c3b5a78e
        ).toArray

        val privateKey0 = "37cc5cb5b54f98f92faef749a53b5ce4e9921890d9fb902b4456957d50791bd0"
        val privateKey1 = "5878ae48fe2d26aa999ed44437cffd2d4ba1543788cff48d490419aef7fc149d"
        val privateKey2 = "3ffaffa96b2fd6542914d3953d05256cd505d4beb6174a2601a4e014c3b5a78e"

        val r4voter0 = KioskGroupElement(stringToGroupElement(ErgoUtil.addressToGroupElement(addresses(0))))
        val r4voter1 = KioskGroupElement(stringToGroupElement(ErgoUtil.addressToGroupElement(addresses(1))))
        val r4voter2 = KioskGroupElement(stringToGroupElement(ErgoUtil.addressToGroupElement(addresses(2))))

        val ballot0Box = KioskBox(ballotAddress, value = 200000000, registers = Array(r4voter0), tokens = Array((ballotTokenId, 1L)))
        val ballot1Box = KioskBox(ballotAddress, value = 200000000, registers = Array(r4voter1), tokens = Array((ballotTokenId, 1L)))
        val ballot2Box = KioskBox(ballotAddress, value = 200000000, registers = Array(r4voter2), tokens = Array((ballotTokenId, 1L)))
      }

      // value to vote for; hash of new bank box script
      val valueVotedFor = KioskCollByte(Blake2b256.hash(bankErgoTree.bytes)) //todo: real update

      // dummy custom input box for funding various transactions
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      // current update box
      val updateBox = ctx
        .newTxBuilder()
        .outBoxBuilder
        .value(minStorageRent)
        .tokens(new ErgoToken(updateNFT, 1))
        .contract(ctx.newContract(ScalaErgoConverters.getAddressFromString(bankUpdateAddress).script))
        .build()
        .convertToInputWith(fakeTxId3, fakeIndex)

      val ballot0InputToCreate = Voters.ballot0Box.copy(
        registers = Array(
          Voters.ballot0Box.registers(0),
          KioskCollByte(updateBox.getId.getBytes),
          valueVotedFor
        )
      )

      val ballot1InputToCreate = Voters.ballot1Box.copy(
        registers = Array(
          Voters.ballot1Box.registers(0),
          KioskCollByte(updateBox.getId.getBytes),
          valueVotedFor
        )
      )

      val ballot2InputToCreate = Voters.ballot2Box.copy(
        registers = Array(
          Voters.ballot2Box.registers(0),
          KioskCollByte(updateBox.getId.getBytes),
          valueVotedFor
        )
      )

      // create ballots
      val ballot0 = TxUtil.createTx(
        inputBoxes = Array(Voters.ballot0Box.toInBox(fakeTxId5, 0), fundingBox),
        dataInputs = Array(),
        boxesToCreate = Array(ballot0InputToCreate),
        fee,
        changeAddress,
        proveDlogSecrets = Array[String](Voters.privateKey0),
        Array[DhtData](),
        false
      ).getOutputsToSpend.get(0)

      val ballot1 = TxUtil.createTx(
        inputBoxes = Array(Voters.ballot1Box.toInBox(fakeTxId6, 0), fundingBox),
        dataInputs = Array(),
        boxesToCreate = Array(ballot1InputToCreate),
        fee,
        changeAddress,
        proveDlogSecrets = Array[String](Voters.privateKey1),
        Array[DhtData](),
        false
      ).getOutputsToSpend.get(0)

      val ballot2 = TxUtil.createTx(
        inputBoxes = Array(Voters.ballot2Box.toInBox(fakeTxId7, 0), fundingBox),
        dataInputs = Array(),
        boxesToCreate = Array(ballot2InputToCreate),
        fee,
        changeAddress,
        proveDlogSecrets = Array[String](Voters.privateKey2),
        Array[DhtData](),
        false
      ).getOutputsToSpend.get(0)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, fakeNanoErgs))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)
      
      val validUpdateOutBox = KioskBox(
        bankUpdateAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((updateNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        fakeNanoErgs,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, fakeNanoErgs))
      )

      val validBallot0Output = Voters.ballot0Box.copy(
        registers = Array(
          Voters.ballot0Box.registers(0)
        )
      )

      val validBallot1Output = Voters.ballot1Box.copy(
        registers = Array(
          Voters.ballot1Box.registers(0)
        )
      )

      val validBallot2Output = Voters.ballot2Box.copy(
        registers = Array(
          Voters.ballot2Box.registers(0)
        )
      )

      noException shouldBe thrownBy {
        TxUtil.createTx(
          Array(updateBox, bankBox, ballot0, ballot1, ballot2, fundingBox),
          Array(),
          Array(validUpdateOutBox, validBankOutBox, validBallot0Output, validBallot1Output, validBallot2Output),
          fee,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }


  property("Bank update should fail if no enough votes collected") {
    ergoClient.execute { implicit ctx: BlockchainContext =>
      object Voters {
        // define voters
        val addresses = Seq(
          "9eiuh5bJtw9oWDVcfJnwTm1EHfK5949MEm5DStc2sD1TLwDSrpx", // private key is 37cc5cb5b54f98f92faef749a53b5ce4e9921890d9fb902b4456957d50791bd0
          "9f9q6Hs7vXZSQwhbrptQZLkTx15ApjbEkQwWXJqD2NpaouiigJQ", // private key is 5878ae48fe2d26aa999ed44437cffd2d4ba1543788cff48d490419aef7fc149d
          "9fGp73EsRQMpFC7xaYD5JFy2abZeKCUffhDBNbQVtBtQyw61Vym", // private key is 3ffaffa96b2fd6542914d3953d05256cd505d4beb6174a2601a4e014c3b5a78e
        ).toArray

        val privateKey0 = "37cc5cb5b54f98f92faef749a53b5ce4e9921890d9fb902b4456957d50791bd0"
        val privateKey1 = "5878ae48fe2d26aa999ed44437cffd2d4ba1543788cff48d490419aef7fc149d"
        val privateKey2 = "3ffaffa96b2fd6542914d3953d05256cd505d4beb6174a2601a4e014c3b5a78e"

        val r4voter0 = KioskGroupElement(stringToGroupElement(ErgoUtil.addressToGroupElement(addresses(0))))
        val r4voter1 = KioskGroupElement(stringToGroupElement(ErgoUtil.addressToGroupElement(addresses(1))))
        val r4voter2 = KioskGroupElement(stringToGroupElement(ErgoUtil.addressToGroupElement(addresses(2))))

        val ballot0Box = KioskBox(ballotAddress, value = 200000000, registers = Array(r4voter0), tokens = Array((ballotTokenId, 1L)))
        val ballot1Box = KioskBox(ballotAddress, value = 200000000, registers = Array(r4voter1), tokens = Array((ballotTokenId, 1L)))
        val ballot2Box = KioskBox(ballotAddress, value = 200000000, registers = Array(r4voter2), tokens = Array((ballotTokenId, 1L)))
      }

      // value to vote for; hash of new bank box script
      val valueVotedFor = KioskCollByte(Blake2b256.hash(bankErgoTree.bytes)) //todo: real update

      // dummy custom input box for funding various transactions
      val fundingBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .contract(ctx.compileContract(ConstantsBuilder.empty(), fakeScript))
          .build()
          .convertToInputWith(fakeTxId1, fakeIndex)

      // current update box
      val updateBox = ctx
        .newTxBuilder()
        .outBoxBuilder
        .value(minStorageRent)
        .tokens(new ErgoToken(updateNFT, 1))
        .contract(ctx.newContract(ScalaErgoConverters.getAddressFromString(bankUpdateAddress).script))
        .build()
        .convertToInputWith(fakeTxId3, fakeIndex)

      val ballot0InputToCreate = Voters.ballot0Box.copy(
        registers = Array(
          Voters.ballot0Box.registers(0),
          KioskCollByte(updateBox.getId.getBytes),
          valueVotedFor
        )
      )

      val ballot1InputToCreate = Voters.ballot1Box.copy(
        registers = Array(
          Voters.ballot1Box.registers(0),
          KioskCollByte(updateBox.getId.getBytes),
          valueVotedFor
        )
      )

      val ballot2InputToCreate = Voters.ballot2Box.copy(
        registers = Array(
          Voters.ballot2Box.registers(0),
          KioskCollByte(updateBox.getId.getBytes),
          valueVotedFor
        )
      )

      // create ballots
      val ballot0 = TxUtil.createTx(
        inputBoxes = Array(Voters.ballot0Box.toInBox(fakeTxId5, 0), fundingBox),
        dataInputs = Array(),
        boxesToCreate = Array(ballot0InputToCreate),
        fee,
        changeAddress,
        proveDlogSecrets = Array[String](Voters.privateKey0),
        Array[DhtData](),
        false
      ).getOutputsToSpend.get(0)

      val ballot1 = TxUtil.createTx(
        inputBoxes = Array(Voters.ballot1Box.toInBox(fakeTxId6, 0), fundingBox),
        dataInputs = Array(),
        boxesToCreate = Array(ballot1InputToCreate),
        fee,
        changeAddress,
        proveDlogSecrets = Array[String](Voters.privateKey1),
        Array[DhtData](),
        false
      ).getOutputsToSpend.get(0)

      val bankBox =
        ctx
          .newTxBuilder()
          .outBoxBuilder
          .value(fakeNanoErgs)
          .tokens(new ErgoToken(bankNFT, 1), new ErgoToken(dexyUSD, fakeNanoErgs))
          .contract(ctx.compileContract(ConstantsBuilder.empty(), bankScript))
          .build()
          .convertToInputWith(fakeTxId4, fakeIndex)

      val validUpdateOutBox = KioskBox(
        bankUpdateAddress,
        minStorageRent,
        registers = Array(),
        tokens = Array((updateNFT, 1))
      )

      val validBankOutBox = KioskBox(
        bankAddress,
        fakeNanoErgs,
        registers = Array(),
        tokens = Array((bankNFT, 1), (dexyUSD, fakeNanoErgs))
      )

      val validBallot0Output = Voters.ballot0Box.copy(
        registers = Array(
          Voters.ballot0Box.registers(0)
        )
      )

      val validBallot1Output = Voters.ballot1Box.copy(
        registers = Array(
          Voters.ballot1Box.registers(0)
        )
      )

      an[Exception] shouldBe thrownBy {
        TxUtil.createTx(
          Array(updateBox, bankBox, ballot0, ballot1, fundingBox),
          Array(),
          Array(validUpdateOutBox, validBankOutBox, validBallot0Output, validBallot1Output),
          fee,
          changeAddress,
          Array[String](),
          Array[DhtData](),
          false
        )
      }
    }
  }

}
