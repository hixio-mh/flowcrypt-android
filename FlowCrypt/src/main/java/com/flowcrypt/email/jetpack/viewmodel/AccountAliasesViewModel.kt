/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.jetpack.viewmodel

import android.accounts.Account
import android.app.Application
import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.flowcrypt.email.api.email.gmail.GmailApiHelper
import com.flowcrypt.email.database.entity.AccountAliasesEntity
import com.flowcrypt.email.database.entity.AccountEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList

/**
 * @author Denis Bondarenko
 *         Date: 1/25/20
 *         Time: 10:20 AM
 *         E-mail: DenBond7@gmail.com
 */
class AccountAliasesViewModel(application: Application) : AccountViewModel(application) {

  val accountAliasesLiveData: LiveData<List<AccountAliasesEntity>> =
    Transformations.switchMap(activeAccountLiveData) {
      roomDatabase.accountAliasesDao().getAliasesLD(it?.email ?: "", it?.accountType ?: "")
    }

  private val freshAccountAliasesLiveData: LiveData<Collection<AccountAliasesEntity>> =
    Transformations
      .switchMap(activeAccountLiveData) { accountEntity ->
        liveData {
          val account = accountEntity?.account ?: return@liveData
          val context: Context = getApplication()
          val aliases: Collection<AccountAliasesEntity> = fetchAliases(context, account)
          emit(aliases)
        }
      }

  fun fetchUpdates(lifecycleOwner: LifecycleOwner) {
    freshAccountAliasesLiveData.observe(lifecycleOwner, Observer { freshAliases ->
      viewModelScope.launch {
        roomDatabase.accountAliasesDao().updateAliases(activeAccountLiveData.value, freshAliases)
      }
    })
  }

  private suspend fun fetchAliases(context: Context, account: Account) =
    withContext(Dispatchers.IO) {
      try {
        val gmailService = GmailApiHelper.generateGmailApiService(context, account)
        val response =
          gmailService.users().settings().sendAs().list(GmailApiHelper.DEFAULT_USER_ID).execute()
        val aliases = ArrayList<AccountAliasesEntity>()
        for (alias in response.sendAs) {
          if (alias.verificationStatus != null) {
            val accountAliasesDao = AccountAliasesEntity(
              email = account.name.toLowerCase(Locale.getDefault()),
              accountType = account.type ?: AccountEntity.ACCOUNT_TYPE_GOOGLE,
              sendAsEmail = alias.sendAsEmail.toLowerCase(Locale.getDefault()),
              displayName = alias.displayName,
              isDefault = alias.isDefault,
              verificationStatus = alias.verificationStatus
            )
            aliases.add(accountAliasesDao)
          }
        }
        aliases
      } catch (e: IOException) {
        e.printStackTrace()
        emptyList<AccountAliasesEntity>()
      }
    }
}
