/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.retrofit.response.node

import android.os.Parcel
import android.os.Parcelable
import com.flowcrypt.email.api.retrofit.response.base.ApiError
import com.flowcrypt.email.api.retrofit.response.base.ApiResponse
import com.flowcrypt.email.api.retrofit.response.model.node.GenericMsgBlock
import com.flowcrypt.email.api.retrofit.response.model.node.MsgBlock
import com.flowcrypt.email.model.MessageEncryptionType
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

/**
 * It's a result for "parseDecryptMsg" requests.
 *
 * @author Denis Bondarenko
 * Date: 1/11/19
 * Time: 3:48 PM
 * E-mail: DenBond7@gmail.com
 */
data class ParseDecryptedMsgResult constructor(
  @Expose val text: String?,
  @Expose val replyType: String,
  @Expose val subject: String?,
  @SerializedName("error")
  @Expose override val apiError: ApiError?,
  var msgBlocks: MutableList<MsgBlock>?
) :
  ApiResponse {

  fun getMsgEncryptionType(): MessageEncryptionType {
    return if (replyType == "encrypted") MessageEncryptionType.ENCRYPTED else MessageEncryptionType.STANDARD
  }

  constructor(source: Parcel) : this(
    source.readString(),
    source.readString()!!,
    source.readString(),
    source.readParcelable<ApiError>(ApiError::class.java.classLoader),
      mutableListOf<MsgBlock>().apply { source.readTypedList(this, GenericMsgBlock.CREATOR) }
  )

  override fun describeContents() = 0

  override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
    writeString(text)
    writeString(replyType)
    writeString(subject)
    writeParcelable(apiError, flags)
    writeTypedList(msgBlocks)
  }

  companion object {
    @JvmField
    val CREATOR: Parcelable.Creator<ParseDecryptedMsgResult> = object : Parcelable.Creator<ParseDecryptedMsgResult> {
      override fun createFromParcel(source: Parcel): ParseDecryptedMsgResult = ParseDecryptedMsgResult(source)
      override fun newArray(size: Int): Array<ParseDecryptedMsgResult?> = arrayOfNulls(size)
    }
  }
}
