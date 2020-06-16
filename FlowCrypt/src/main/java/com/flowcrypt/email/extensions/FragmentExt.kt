/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.extensions

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.flowcrypt.email.ui.activity.fragment.dialog.InfoDialogFragment

/**
 * @author Denis Bondarenko
 *         Date: 6/16/20
 *         Time: 3:31 PM
 *         E-mail: DenBond7@gmail.com
 */
fun Fragment.showDialogFragment(dialog: DialogFragment) {
  val fragmentTransaction: FragmentTransaction = parentFragmentManager.beginTransaction()
  parentFragmentManager.findFragmentByTag("dialog")?.let { fragmentTransaction.remove(it) }
  fragmentTransaction.addToBackStack(null)
  dialog.show(fragmentTransaction, "dialog")
}

fun Fragment.showInfoDialogFragment(dialogTitle: String? = "", dialogMsg: String? = null,
                                    buttonTitle: String? = null, isPopBackStack: Boolean = false,
                                    isCancelable: Boolean = true, hasHtml: Boolean = false,
                                    targetFragment: Fragment? = null, requestCode: Int = 0) {
  val infoDialogFragment = InfoDialogFragment.newInstance(
      dialogTitle = dialogTitle,
      dialogMsg = dialogMsg,
      buttonTitle = buttonTitle,
      isPopBackStack = isPopBackStack,
      isCancelable = isCancelable,
      hasHtml = hasHtml)


  targetFragment?.let {
    infoDialogFragment.setTargetFragment(targetFragment, requestCode)
  }

  showDialogFragment(infoDialogFragment)
}