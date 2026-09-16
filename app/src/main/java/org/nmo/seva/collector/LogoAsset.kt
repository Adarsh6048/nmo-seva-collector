package org.nmo.seva.collector

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.ImageView

object LogoAsset {
    private val encoded: String = "UklGRrQxAABXRUJQVlA4IKgxAADQoACdASoAAfMAPnkwk0YkoyGhMFgL8JAPCWwIc5GO2E396/IzuMOFdy/o37Ifld8pVdfuv9s/VX93/bP5F9gfWnl1eb/tn/N/zP5SfNn/X/9P2Pfqn/qe4T+pf/C/x3+S/ZX4z/2A91H7o+of9g/2b93D/o/td7yP73/uP2t+Ar+k...TRUNCATED..."

    fun applyTo(view: ImageView) {
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        view.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }
}
