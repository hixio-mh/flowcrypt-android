/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.matchers

import android.view.WindowManager
import androidx.test.espresso.Root
import androidx.test.espresso.remote.annotation.RemoteMsgConstructor
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher

/**
 * @author Denis Bondarenko
 *         Date: 6/7/21
 *         Time: 11:37 AM
 *         E-mail: DenBond7@gmail.com
 *
 * See details here
 * https://stackoverflow.com/questions/28390574/checking-toast-message-in-android-espresso
 */
class ToastMatcher @RemoteMsgConstructor constructor() : TypeSafeMatcher<Root?>() {
  override fun describeTo(description: Description) {
    description.appendText("is toast")
  }

  @SuppressWarnings("deprecation")
  public override fun matchesSafely(root: Root?): Boolean {
    val type = root?.windowLayoutParams?.get()?.type
    if (type == WindowManager.LayoutParams.TYPE_TOAST) {
      val windowToken = root.decorView.windowToken
      val appToken = root.decorView.applicationWindowToken
      if (windowToken === appToken) { // means this window isn't contained by any other windows.
        return true
      }
    }
    return false
  }
}
