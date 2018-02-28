package uk.co.sundroid.activity.data.fragments

import android.content.Intent
import uk.co.sundroid.AbstractFragment
import uk.co.sundroid.activity.data.DataActivity
import uk.co.sundroid.activity.data.fragments.dialogs.OnViewPrefsChangedListener
import uk.co.sundroid.activity.location.TimeZonePickerActivity
import uk.co.sundroid.domain.LocationDetails
import uk.co.sundroid.util.log.*
import uk.co.sundroid.util.prefs.SharedPrefsHelper

import java.util.Calendar

/**
 * Parent class for fragments that show data.
 */
abstract class AbstractDataFragment : AbstractFragment(), OnViewPrefsChangedListener {

    protected var isSafe: Boolean = false
        get() = activity != null && !isDetached && applicationContext != null

    protected var location: LocationDetails? = null
        get() = SharedPrefsHelper.getSelectedLocation(applicationContext!!)

    protected var dateCalendar: Calendar? = null
        get() = if (activity is DataActivity) {
            (activity as DataActivity).dateCalendar
        } else null

    protected var timeCalendar: Calendar? = null
        get() = if (activity is DataActivity) {
            (activity as DataActivity).timeCalendar
        } else null

    abstract fun initialise()

    abstract fun update()

    override fun onViewPrefsUpdated() {
        try {
            initialise()
            update()
        } catch (e: Exception) {
            e(TAG, "Initialise for settings change failed", e)
        }

    }

    protected fun startTimeZone() {
        val intent = Intent(activity, TimeZonePickerActivity::class.java)
        intent.putExtra(TimeZonePickerActivity.INTENT_MODE, TimeZonePickerActivity.MODE_CHANGE)
        activity.startActivityForResult(intent, TimeZonePickerActivity.REQUEST_TIMEZONE)
    }

    companion object {
        private val TAG = AbstractDataFragment::class.java.simpleName
    }

}