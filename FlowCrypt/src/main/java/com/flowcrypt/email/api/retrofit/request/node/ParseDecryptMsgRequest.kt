/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.retrofit.request.node

import android.content.Context
import android.net.Uri
import com.flowcrypt.email.api.retrofit.node.NodeService
import com.flowcrypt.email.api.retrofit.request.model.node.PrivateKeyInfo
import com.flowcrypt.email.security.model.PgpKeyDetails
import com.google.gson.annotations.Expose
import retrofit2.Response

/**
 * This class will be used for the message decryption.
 *
 * @author Denis Bondarenko
 * Date: 1/11/19
 * Time: 03:29 PM
 * E-mail: DenBond7@gmail.com
 */
class ParseDecryptMsgRequest @JvmOverloads constructor(
  context: Context? = null,
  override val data: ByteArray = ByteArray(0),
  override val uri: Uri? = null,
  override val hasEncryptedDataInUri: Boolean = false,
  pgpKeyDetailsList: List<PgpKeyDetails>,
  @Expose val isEmail: Boolean = false
) : BaseNodeRequest(context, uri) {

  @Expose
  private val keys: List<PrivateKeyInfo> = pgpKeyDetailsList.map {
    PrivateKeyInfo(
      privateKey = it.privateKey ?: throw IllegalArgumentException("Empty private key"),
      longid = it.fingerprint,
      passphrase = String(it.tempPassphrase ?: CharArray(0))
    )
  }

  override val endpoint: String = "parseDecryptMsg"

  override fun getResponse(nodeService: NodeService): Response<*> {
    return nodeService.parseDecryptMsgOld(this).execute()
  }
}
