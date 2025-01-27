/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.flowcrypt.email.R
import com.flowcrypt.email.api.retrofit.response.base.Result
import com.flowcrypt.email.databinding.FragmentSearchBackupsInEmailBinding
import com.flowcrypt.email.extensions.decrementSafely
import com.flowcrypt.email.extensions.exceptionMsg
import com.flowcrypt.email.extensions.incrementSafely
import com.flowcrypt.email.extensions.navController
import com.flowcrypt.email.extensions.toast
import com.flowcrypt.email.jetpack.viewmodel.BackupsViewModel
import com.flowcrypt.email.ui.activity.fragment.base.BaseFragment
import com.flowcrypt.email.ui.activity.fragment.base.ProgressBehaviour

/**
 * @author DenBond7
 * Date: 30.05.2017
 * Time: 15:27
 * E-mail: DenBond7@gmail.com
 */
class SearchBackupsInEmailFragment : BaseFragment(), ProgressBehaviour {
  private var binding: FragmentSearchBackupsInEmailBinding? = null
  private val backupsViewModel: BackupsViewModel by viewModels()

  override val progressView: View?
    get() = binding?.iProgress?.root
  override val contentView: View?
    get() = binding?.gContent
  override val statusView: View?
    get() = binding?.iStatus?.root

  override val contentResourceId: Int = R.layout.fragment_search_backups_in_email

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    binding = FragmentSearchBackupsInEmailBinding.inflate(inflater, container, false)
    return binding?.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    supportActionBar?.title = getString(R.string.backups)
    initViews()
    initBackupsViewModel()
  }

  private fun initViews() {
    binding?.btBackup?.setOnClickListener {
      navController?.navigate(
        SearchBackupsInEmailFragmentDirections
          .actionSearchBackupsInEmailFragmentToBackupKeysFragment()
      )
    }
  }

  private fun initBackupsViewModel() {
    backupsViewModel.onlineBackupsLiveData.observe(viewLifecycleOwner, {
      when (it.status) {
        Result.Status.LOADING -> {
          baseActivity.countingIdlingResource.incrementSafely()
          showProgress()
        }

        Result.Status.SUCCESS -> {
          val keys = it.data
          if (keys.isNullOrEmpty()) {
            binding?.tVTitle?.text = getText(R.string.there_are_no_backups_on_this_account)
            binding?.btBackup?.text = getString(R.string.back_up_my_key)
          } else {
            binding?.tVTitle?.text = getString(R.string.backups_found, keys.size)
            binding?.btBackup?.text = getString(R.string.see_more_backup_options)
          }
          showContent()
          baseActivity.countingIdlingResource.decrementSafely()
        }

        Result.Status.EXCEPTION -> {
          toast(it.exceptionMsg)
          baseActivity.countingIdlingResource.decrementSafely()
          navController?.navigateUp()
        }

        else -> {
          baseActivity.countingIdlingResource.decrementSafely()
        }
      }
    })
  }
}
