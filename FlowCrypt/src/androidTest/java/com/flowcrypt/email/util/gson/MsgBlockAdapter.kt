/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.util.gson

import com.flowcrypt.email.api.retrofit.response.model.node.DecryptErrorMsgBlock
import com.flowcrypt.email.api.retrofit.response.model.node.GenericMsgBlock
import com.flowcrypt.email.api.retrofit.response.model.node.MsgBlock
import com.flowcrypt.email.api.retrofit.response.model.node.PublicKeyMsgBlock
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

/**
 * @author Denis Bondarenko
 * Date: 3/19/19
 * Time: 2:58 PM
 * E-mail: DenBond7@gmail.com
 */
class MsgBlockAdapter : JsonDeserializer<MsgBlock> {
  override fun deserialize(
    json: JsonElement,
    typeOfT: Type,
    context: JsonDeserializationContext
  ): MsgBlock? {
    val jsonObject = json.asJsonObject

    val type = context.deserialize<MsgBlock.Type>(jsonObject.get("type"), MsgBlock.Type::class.java)
      ?: return null

    when (type) {
      MsgBlock.Type.PUBLIC_KEY -> return context.deserialize<MsgBlock>(
        json,
        PublicKeyMsgBlock::class.java
      )

      MsgBlock.Type.DECRYPT_ERROR -> return context.deserialize<MsgBlock>(
        json,
        DecryptErrorMsgBlock::class.java
      )

      else -> return context.deserialize<MsgBlock>(json, GenericMsgBlock::class.java)
    }
  }
}
