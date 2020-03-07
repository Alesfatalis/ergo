package org.ergoplatform.local

import org.ergoplatform.modifiers.history.popow.{PoPowProof, PoPowParams}
import org.ergoplatform.modifiers.history.Header
import scorex.util.ModifierId

class PoPoWVerifier(poPoWParams: PoPowParams, genesisId: ModifierId) {
    // TODO: ideally prefix would have the actual genesis but we may just not have it
    var bestProof: Option[PoPowProof] = None

    def bestChain(): Seq[Header] = {
        bestProof.map(_.headersChain).getOrElse(Seq())
    }

    def process(newProof: PoPowProof) {
        if (newProof.headersChain.head.id == genesisId &&
          !bestProof.exists(_.isBetterThan(newProof))) {
          bestProof = Some(newProof)
        }
    }
}