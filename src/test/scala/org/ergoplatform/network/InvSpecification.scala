package org.ergoplatform.network

import java.nio.ByteBuffer

import com.google.common.primitives.Ints
import org.ergoplatform.modifiers.history.Header
import org.ergoplatform.utils.ErgoPropertyTest
import scorex.core.network.message.{InvData, InvSpec, Message, MessageSerializer}
import scorex.util.ModifierId
import scorex.util.encode.Base16

class InvSpecification extends ErgoPropertyTest with DecodingUtils {

  property("inv reference parser") {
    val magic = Array(1: Byte, 0: Byte, 2: Byte, 4: Byte) // mainnet magic
    val invSpec = new InvSpec(maxInvObjects = 100)

    val headerId = Array.fill(16)(1: Byte) ++ Array.fill(16)(2: Byte)

    val headerIdEncoded = ModifierId @@ Base16.encode(headerId)

    val invData = InvData(Header.modifierTypeId, Seq(headerIdEncoded))

    val invMessage = Message(invSpec, Right(invData), None)

    val ms = new MessageSerializer(Seq(invSpec), magic)

    val bs = ms.serialize(invMessage).toArray
    val bsString = Base16.encode(bs)

    bsString shouldBe "0100020437000000226abfdbf565010101010101010101010101010101010102020202020202020202020202020202"

    val bb = ByteBuffer.wrap(bs)

    val magicRead = getBytes(bb, 4)
    magicRead.toIndexedSeq shouldBe magic.toIndexedSeq

    val messageCode = getByte(bb)
    messageCode shouldBe invSpec.messageCode  // 65 (in dec)

    val messageLength = Ints.fromByteArray(getBytes(bb,4))

    messageLength shouldBe 34

    val checkSum = getBytes(bb, 4)

    val modifierTypeId = getByte(bb) // should read one byte only

    modifierTypeId shouldBe 101.toByte // type id corresponding to block header

    val headersCount = getULong(bb).toInt // should read up to 4 bytes max

    headersCount shouldBe 1

    val headerIdParsed = getBytes(bb, 32)

    headerIdParsed.toIndexedSeq shouldBe headerId.toIndexedSeq
  }

}
