package org.ergoplatform.nodeView.wallet

import org.ergoplatform.ErgoBox.NonMandatoryRegisterId
import org.ergoplatform._
import org.ergoplatform.db.DBSpec
import org.ergoplatform.nodeView.wallet.persistence.{OffChainRegistry, WalletRegistry, WalletStorage}
import org.ergoplatform.nodeView.wallet.requests.{AssetIssueRequest, PaymentRequest}
import org.ergoplatform.utils.fixtures.WalletFixture
import org.ergoplatform.utils.generators.ErgoTransactionGenerators
import org.ergoplatform.utils.{ErgoPropertyTest, WalletTestOps}
import org.ergoplatform.wallet.Constants.PaymentsScanId
import org.ergoplatform.wallet.boxes.BoxSelector.BoxSelectionResult
import org.ergoplatform.wallet.boxes.{ErgoBoxSerializer, ReplaceCompactCollectBoxSelector, TrackedBox}
import org.scalatest.BeforeAndAfterAll
import scorex.util.encode.Base16
import sigmastate.Values.{ByteArrayConstant, EvaluatedValue}
import sigmastate.helpers.TestingHelpers.testBox
import sigmastate.{SType, Values}


class ErgoWalletServiceSpec extends ErgoPropertyTest with WalletTestOps with ErgoWalletSupport with ErgoTransactionGenerators with DBSpec with BeforeAndAfterAll {

  private implicit val x: WalletFixture = new WalletFixture(settings, getCurrentView(_).vault)
  private lazy val pks = getPublicKeys.toList

  override def afterAll(): Unit = try super.afterAll() finally x.stop()

  property("prepareUnsignedTransaction should yield valid tx") {
    val inputBoxes = {
      Seq(
        TrackedBox(
          ErgoLikeTransaction(IndexedSeq(), IndexedSeq()),
          creationOutIndex = 0,
          None,
          testBox(1L, Values.TrueLeaf.toSigmaProp, 0),
          Set(PaymentsScanId)
        )
      )
    }

    forAll(ergoBoxCandidateGen, ergoBoxCandidateGen, validErgoTransactionGen, proveDlogGen) {
      case (outputCandidate, outputChangeCandidate, (ergoBoxes, _), proveDlog) =>
        val selectionResult = BoxSelectionResult(inputBoxes, Seq(outputChangeCandidate))
        val tx = prepareUnsignedTransaction(Seq(outputCandidate), startHeight, selectionResult, ergoBoxes, Option(proveDlog)).get
        tx.inputs shouldBe inputBoxes.map(_.box.id).map(id => new UnsignedInput(id))
        tx.dataInputs shouldBe ergoBoxes.map(dataInputBox => DataInput(dataInputBox.id))
        tx.outputCandidates.size shouldBe 2
        tx.outputCandidates.map(_.value).sum shouldBe outputCandidate.value + outputChangeCandidate.value

        val txWithChangeBoxesButNoChangeAddress =
          prepareUnsignedTransaction(Seq(outputCandidate), startHeight, selectionResult, ergoBoxes, Option.empty)
        txWithChangeBoxesButNoChangeAddress.isFailure shouldBe true
    }
  }

  property("requestsToBoxCandidates should process payment request") {
    forAll(validErgoTransactionGen) {
      case (ergoBoxes, _) =>
        val paymentRequest = PaymentRequest(pks.head, 1, Seq.empty, Map.empty)
        val paymentCandidates = requestsToBoxCandidates(Seq(paymentRequest), ergoBoxes.head.id, startHeight, parameters, pks).get
        paymentCandidates shouldBe List(new ErgoBoxCandidate(value = 1, ergoTree = pks.head.script, startHeight))
    }
  }

  property("requestsToBoxCandidates should process asset issue request") {
    forAll(validErgoTransactionGen) {
      case (ergoBoxes, _) =>
        val ergoBox = ergoBoxes.head

        val registers: Option[Map[NonMandatoryRegisterId, EvaluatedValue[_ <: SType]]] = Option(Map(ErgoBox.R4 -> sigmastate.Values.FalseLeaf))
        val illegalAssetIssueRequest = AssetIssueRequest(address = pks.head, Some(1), amount = 1, "test", "test", 4, registers)
        val invalidCandidates = requestsToBoxCandidates(Seq(illegalAssetIssueRequest), ergoBox.id, startHeight, parameters, pks)
        invalidCandidates.failed.get.getMessage shouldBe "Additional registers contain R0...R6"

        val assetIssueRequestWithoutAddress = AssetIssueRequest(addressOpt = Option.empty, Some(1), amount = 1, "test", "test", 4, Option.empty)
        val missingAddressCandidates = requestsToBoxCandidates(Seq(assetIssueRequestWithoutAddress), ergoBox.id, startHeight, parameters, Seq.empty)
        missingAddressCandidates.failed.get.getMessage shouldBe "No address available for box locking"

        val assetIssueRequestWithoutValue = AssetIssueRequest(address = pks.head, valueOpt = Option.empty, amount = 1, "test", "test", 4, Option.empty)
        val missingValueCandidates = requestsToBoxCandidates(Seq(assetIssueRequestWithoutValue), ergoBox.id, startHeight, parameters, Seq.empty).get.head
        missingValueCandidates.value > 0 shouldBe true

        val assetIssueRequest = AssetIssueRequest(address = pks.head, Some(1), amount = 1, "test-name", "test-description", 4, Option.empty)
        val validCandidate = requestsToBoxCandidates(Seq(assetIssueRequest), ergoBox.id, startHeight, parameters, Seq.empty).get.head
        validCandidate.value shouldBe 1
        validCandidate.additionalRegisters shouldBe
          Map(
            ErgoBox.R4 -> ByteArrayConstant("test-name".getBytes("UTF-8")),
            ErgoBox.R5 -> ByteArrayConstant("test-description".getBytes("UTF-8")),
            ErgoBox.R6 -> ByteArrayConstant("4".getBytes("UTF-8")),
          )
        validCandidate.additionalTokens.toMap shouldBe Map(ergoBox.id -> 1)
        validCandidate.creationHeight shouldBe startHeight
        validCandidate.ergoTree shouldBe pks.head.script
    }
  }

  property("generateUnsignedTransaction should return valid tx") {
    withVersionedStore(2) { versionedStore =>
      withStore { store =>
        val wState =
          ErgoWalletState(
            new WalletStorage(store, settings),
            secretStorageOpt = Option.empty,
            new WalletRegistry(versionedStore)(settings.walletSettings),
            OffChainRegistry.empty,
            outputsFilter = Option.empty,
            WalletVars(Some(defaultProver), Seq.empty, None),
            stateReaderOpt = Option.empty,
            mempoolReaderOpt = Option.empty,
            utxoReaderOpt = Option.empty,
            parameters
          )

        val encodedBoxes =
          boxesAvailable(makeGenesisBlock(pks.head.pubkey, randomNewAsset), pks.head.pubkey)
            .map { box =>
              Base16.encode(ErgoBoxSerializer.toBytes(box))
            }
        val paymentRequest = PaymentRequest(pks.head, 50000, Seq.empty, Map.empty)
        val boxSelector = new ReplaceCompactCollectBoxSelector(settings.walletSettings.maxInputs, settings.walletSettings.optimalInputs)
        val (tx, inputs, dataInputs) = generateUnsignedTransaction(wState, boxSelector, Seq(paymentRequest), inputsRaw = encodedBoxes, dataInputsRaw = Seq.empty).get

        dataInputs shouldBe empty
        inputs.size shouldBe 1
        tx.inputs.size shouldBe 1
        tx.outputs.size shouldBe 2
        tx.outputs.map(_.value).sum shouldBe inputs.map(_.value).sum
      }
    }
  }

}
