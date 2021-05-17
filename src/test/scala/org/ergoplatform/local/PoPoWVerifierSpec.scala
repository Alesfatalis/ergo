package org.ergoplatform.local

import org.ergoplatform.modifiers.history.popow.{PoPowHeader, PoPowParams}
import org.ergoplatform.modifiers.ErgoFullBlock
import org.ergoplatform.utils.generators.{ChainGenerator, ErgoGenerators}
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec


class PoPoWVerifierSpec extends AnyPropSpec with Matchers with ChainGenerator with ErgoGenerators {

  private val poPowParams = PoPowParams(30, 30)
  val toPoPoWChain = (c: Seq[ErgoFullBlock]) =>
    c.map(b => PoPowHeader(b.header, popowAlgos.unpackInterlinks(b.extension.fields).get))

  property("processes new proofs") {
    val sizes = Seq(1000)
    sizes.foreach { size =>
      val baseChain = genChain(size)
      val branchPoint = baseChain.last
      val shortChain = toPoPoWChain(baseChain)
      val longChain = toPoPoWChain(baseChain ++ genChain(5, branchPoint).tail)

      val shortProof = popowAlgos.prove(shortChain)(poPowParams)
      val longProof = popowAlgos.prove(longChain)(poPowParams)

      val verifier = new PoPoWVerifier(poPowParams, baseChain.head.id)
      verifier.bestChain.length shouldBe 0
      verifier.process(shortProof)
      verifier.bestChain.length should be > 0
      verifier.process(longProof)
      verifier.bestChain.last.id shouldBe longProof.headersChain.last.id
    }
  }
}
