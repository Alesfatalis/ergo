package org.ergoplatform.nodeView.wallet.persistence

import org.ergoplatform.ErgoBox.{BoxId, TokenId}
import org.ergoplatform.settings.Constants
import scorex.core.serialization.ScorexSerializer
import scorex.crypto.authds.ADKey
import scorex.crypto.hash.Digest32
import scorex.util.serialization.{Reader, Writer}

final case class RegistryIndex(height: Int,
                               balance: Long,
                               assetBalances: Seq[(TokenId, Long)],
                               uncertainBoxes: Seq[BoxId]) {

  override def equals(obj: Any): Boolean = obj match {
    case that: RegistryIndex =>
      val equalHeight = that.height == this.height
      val equalBalance = that.balance == this.balance
      val equalTokens = that.assetBalances.zip(this.assetBalances).forall { case ((x1, y1), (x2, y2)) =>
        java.util.Arrays.equals(x1, x2) && y1 == y2
      }
      val equalUncertain = that.uncertainBoxes.zip(this.uncertainBoxes).forall { case (x1, x2) =>
        java.util.Arrays.equals(x1, x2)
      }
      equalHeight && equalBalance && equalTokens && equalUncertain
    case _ =>
      false
  }

}

object RegistryIndexSerializer extends ScorexSerializer[RegistryIndex] {

  override def serialize(obj: RegistryIndex, w: Writer): Unit = {
    w.putInt(obj.height)
    w.putLong(obj.balance)
    w.putInt(obj.assetBalances.size)
    obj.assetBalances.foreach { case (id, amt) =>
      w.putBytes(id)
      w.putLong(amt)
    }
    w.putInt(obj.uncertainBoxes.size)
    obj.uncertainBoxes.foreach(x => w.putBytes(x))
  }

  override def parse(r: Reader): RegistryIndex = {
    val height = r.getInt()
    val balance = r.getLong()
    val tokensQty = r.getInt()
    val tokenBalances = (0 until tokensQty).map { _ =>
      Digest32 @@ r.getBytes(Constants.ModifierIdSize) -> r.getLong()
    }
    val uncertainQty = r.getInt()
    val uncertainIds = (0 until uncertainQty).map { _ =>
      ADKey @@ r.getBytes(Constants.ModifierIdSize)
    }
    RegistryIndex(height, balance, tokenBalances, uncertainIds)
  }

}
