/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.jetpack.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowcrypt.email.R
import com.flowcrypt.email.api.retrofit.node.NodeRepository
import com.flowcrypt.email.api.retrofit.node.PgpApiRepository
import com.flowcrypt.email.api.retrofit.request.node.ParseKeysRequest
import com.flowcrypt.email.api.retrofit.response.base.ApiError
import com.flowcrypt.email.api.retrofit.response.base.Result
import com.flowcrypt.email.api.retrofit.response.model.node.NodeKeyDetails
import com.flowcrypt.email.model.PublicKeyInfo
import com.flowcrypt.email.util.GeneralUtil
import com.flowcrypt.email.util.exception.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

/**
 * This [ViewModel] implementation can be used to fetch details about the given keys.
 *
 * @author Denis Bondarenko
 *         Date: 9/21/19
 *         Time: 2:24 PM
 *         E-mail: DenBond7@gmail.com
 */
class ParseKeysViewModel(application: Application) : BaseNodeApiViewModel(application) {
  private val apiRepository: PgpApiRepository = NodeRepository()
  val parsedKeysFromSourceLiveData: MutableLiveData<Result<List<PublicKeyInfo>>> = MutableLiveData()

  fun fetchKeys(rawKey: String?) {
    //todo-denbond7 need to change it to use the common approach with LiveData
    apiRepository.fetchKeyDetails(R.id.live_data_id_fetch_keys, responsesLiveData, rawKey)
  }

  fun parseKeys(publicKeysFileUri: Uri?) {
    publicKeysFileUri ?: return
    viewModelScope.launch {
      val armoredKeys = GeneralUtil.readFileFromUriToString(getApplication(), publicKeysFileUri)
      parseKeys(armoredKeys)
    }
  }

  fun parseKeys(armoredKeys: String? = null) {
    armoredKeys ?: return

    viewModelScope.launch {
      parsedKeysFromSourceLiveData.postValue(Result.loading())
      val request = ParseKeysRequest(armoredKeys)
      val kedDetailsResult = apiRepository.fetchKeyDetails(request)

      if (kedDetailsResult.status == Result.Status.SUCCESS) {
        parsedKeysFromSourceLiveData.postValue(Result.success(parsePublicKeysInfo(kedDetailsResult.data?.nodeKeyDetails)))
      } else {
        when (kedDetailsResult.status) {
          Result.Status.ERROR -> {
            parsedKeysFromSourceLiveData.postValue(Result.exception(ApiException(kedDetailsResult.data?.apiError
                ?: ApiError(code = -1, msg = "Unknown API error"))))
          }

          else -> {
            parsedKeysFromSourceLiveData.postValue(Result.exception(kedDetailsResult.exception
                ?: java.lang.Exception()))
          }
        }
      }
    }
  }

  private suspend fun parsePublicKeysInfo(details: List<NodeKeyDetails>?): List<PublicKeyInfo> {
    details ?: return emptyList()
    val publicKeyInfoList = ArrayList<PublicKeyInfo>()

    val emails = HashSet<String>()

    val blocksCount = details.size
    var progress: Float
    var lastProgress = 0f

    for (i in 0 until blocksCount) {
      val nodeKeyDetails = details[i]
      val publicKeyInfo = getPublicKeyInfo(nodeKeyDetails, emails)

      if (publicKeyInfo != null) {
        publicKeyInfoList.add(publicKeyInfo)
      }

      progress = i * 100f / blocksCount
      if (progress - lastProgress >= 1) {
        parsedKeysFromSourceLiveData.postValue(Result.loading(progress = progress.toInt()))
        lastProgress = progress
      }
    }

    parsedKeysFromSourceLiveData.postValue(Result.loading(progress = 100))

    return publicKeyInfoList
  }

  private suspend fun getPublicKeyInfo(nodeKeyDetails: NodeKeyDetails, emails: MutableSet<String>): PublicKeyInfo? = withContext(Dispatchers.IO) {
    val fingerprint = nodeKeyDetails.fingerprint ?: ""
    val longId = nodeKeyDetails.longId ?: ""
    val keyWords = nodeKeyDetails.keywords ?: ""
    var keyOwner: String? = nodeKeyDetails.primaryPgpContact.email

    if (keyOwner != null) {
      keyOwner = keyOwner.toLowerCase(Locale.getDefault())

      if (emails.contains(keyOwner)) {
        return@withContext null
      }

      emails.add(keyOwner)

      val contact = roomDatabase.contactsDao().getContactByEmailSuspend(keyOwner)?.toPgpContact()
      return@withContext PublicKeyInfo(keyWords, fingerprint, keyOwner, longId, contact, nodeKeyDetails.publicKey!!)
    }
    return@withContext null
  }
}