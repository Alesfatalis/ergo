package org.ergoplatform.modifiers.history.popow

import org.ergoplatform.modifiers.ErgoPersistentModifier
import org.ergoplatform.modifiers.history.Header
import org.ergoplatform.modifiers.history.popow.PoPowAlgos.{bestArg, lowestCommonAncestor, maxLevelOf}
import org.ergoplatform.settings.Algos
import scorex.core.ModifierTypeId
import scorex.core.serialization.ScorexSerializer
import scorex.util.ModifierId
import scorex.util.serialization.{Reader, Writer}
import scorex.util.Extensions.LongOps

/**
  * A structure representing NiPoPow proof as a persistent modifier.
  * @param m        - security parameter (min μ-level superchain length)
  * @param k        - security parameter (min suffix length)
  * @param prefix   - proof prefix headers
  * @param suffix   - proof suffix headers
  */
case class PoPowProof(m: Int, k: Int, prefix: Seq[PoPowHeader], suffix: Seq[PoPowHeader])
  extends ErgoPersistentModifier {

  override type M = PoPowProof

  override val modifierTypeId: ModifierTypeId = PoPowProof.modifierTypeId

  override val sizeOpt: Option[Int] = None

  override def serializedId: Array[Byte] = Algos.hash(bytes)

  override def serializer: ScorexSerializer[M] = PoPowProofSerializer

  override def parentId: ModifierId = chain.head.id

  def chain: Seq[PoPowHeader] = prefix ++ suffix

  def headersChain: Seq[Header] = chain.map(_.header)

  def prefixHeaders: Seq[Header] = prefix.map(_.header)

  def suffixHeaders: Seq[Header] = suffix.map(_.header)

  def chainOfLevel(l: Int): Seq[PoPowHeader] = prefix.filter(x => maxLevelOf(x.header) >= l)

  def isBetterThan(that: PoPowProof): Boolean = {
    val (thisDivergingChain, thatDivergingChain) = lowestCommonAncestor(headersChain, that.headersChain)
      .map(h => headersChain.filter(_.height > h.height) -> that.headersChain.filter(_.height > h.height))
      .getOrElse(headersChain -> that.headersChain)
    bestArg(thisDivergingChain)(m) > bestArg(thatDivergingChain)(m)
  }
}


object PoPowProof {
  val modifierTypeId: ModifierTypeId = ModifierTypeId @@ (110: Byte)
}

object PoPowProofSerializer extends ScorexSerializer[PoPowProof] {

  override def serialize(obj: PoPowProof, w: Writer): Unit = {
    w.putUInt(obj.m)
    w.putUInt(obj.k)
    w.putUInt(obj.prefix.size)
    obj.prefix.foreach { h =>
      val hBytes = h.bytes
      w.putUInt(hBytes.length)
      w.putBytes(hBytes)
    }
    w.putUInt(obj.suffix.size)
    obj.suffix.foreach { h =>
      val hBytes = h.bytes
      w.putUInt(hBytes.length)
      w.putBytes(hBytes)
    }
  }

  override def parse(r: Reader): PoPowProof = {
    val m = r.getUInt().toIntExact
    val k = r.getUInt().toIntExact
    val prefixSize = r.getUInt().toIntExact
    val prefix = (0 until prefixSize).map { _ =>
      val size = r.getUInt().toIntExact
      PoPowHeaderSerializer.parseBytes(r.getBytes(size))
    }
    val suffixSize = r.getUInt().toIntExact
    val suffix = (0 until suffixSize).map { _ =>
      val size = r.getUInt().toIntExact
      PoPowHeaderSerializer.parseBytes(r.getBytes(size))
    }
    PoPowProof(m, k, prefix, suffix)
  }

}
