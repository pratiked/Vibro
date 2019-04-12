package demo.pratiked.vibro.models

import java.io.Serializable

class Audio(var data: String?, var title: String?, var album: String?, var artist: String?,
            var composer: String?, var displayName: String?, var duration: String?, /*millisec*/
            var mimeType: String?, var size: String? /*bytes*/)
    : Serializable
