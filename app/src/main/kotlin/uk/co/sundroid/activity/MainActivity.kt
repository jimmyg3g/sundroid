package uk.co.sundroid.activity

import android.content.Intent
import android.os.Bundle

import kotlinx.android.synthetic.main.main.*
import android.support.v7.app.ActionBarDrawerToggle
import uk.co.sundroid.AbstractActivity
import uk.co.sundroid.R
import uk.co.sundroid.activity.data.DataActivity
import uk.co.sundroid.activity.data.DataGroup
import uk.co.sundroid.activity.data.fragments.*
import uk.co.sundroid.domain.LocationDetails
import uk.co.sundroid.util.location.LatitudeLongitude
import uk.co.sundroid.util.prefs.Prefs
import uk.co.sundroid.util.time.TimeZoneResolver
import java.util.*

class MainActivity : AbstractActivity() {

    private var dataGroup: DataGroup = DataGroup.DAY_DETAIL

    var dateCalendar: Calendar = Calendar.getInstance()
    var timeCalendar: Calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        setSupportActionBar(toolbar)
        initNavigationDrawer()

        var forceDateUpdate = false
        if (intent.action != null && intent.action == Intent.ACTION_MAIN) {
            forceDateUpdate = true
            intent.action = null
        }

        Prefs.initPreferences(this)
        initCalendarAndLocation(forceDateUpdate)
        restoreState(savedInstanceState)
        setDataGroup(dataGroup, true)
    }

    private fun initCalendarAndLocation(forceDateUpdate: Boolean) {
        val location = Prefs.selectedLocation(this) ?: LocationDetails(LatitudeLongitude(37.779093, -122.419109)).apply {
            name = "San Francisco"
            timeZone = TimeZoneResolver.getTimeZone("US/Pacific")
            Prefs.saveSelectedLocation(this@MainActivity, this)
        }

        var dateDonor = dateCalendar
        var timeDonor = timeCalendar
        if (forceDateUpdate) {
            dateDonor = Calendar.getInstance()
            timeDonor = Calendar.getInstance()
        }
        dateCalendar.timeZone = location.timeZone!!.zone
        dateCalendar.set(dateDonor.get(Calendar.YEAR), dateDonor.get(Calendar.MONTH), dateDonor.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        dateCalendar.set(Calendar.MILLISECOND, 0)
        dateCalendar.timeInMillis
        timeCalendar.timeZone = location.timeZone!!.zone
        timeCalendar.set(dateDonor.get(Calendar.YEAR), dateDonor.get(Calendar.MONTH), dateDonor.get(Calendar.DAY_OF_MONTH), timeDonor.get(Calendar.HOUR_OF_DAY), timeDonor.get(Calendar.MINUTE), 0)
        timeCalendar.set(Calendar.MILLISECOND, 0)
        timeCalendar.timeInMillis
    }

    override fun onSaveInstanceState(state: Bundle?) {
        super.onSaveInstanceState(state)
        state?.putSerializable(STATE_DATA_GROUP, this.dataGroup)
        state?.putLong(STATE_DATE_TIMESTAMP, this.dateCalendar.timeInMillis)
        state?.putLong(STATE_TIME_TIMESTAMP, this.timeCalendar.timeInMillis)
    }

    private fun restoreState(state: Bundle?) {
        this.dataGroup = DataGroup.DAY_SUMMARY
        if (state != null) {
            if (state.containsKey(STATE_DATA_GROUP)) {
                this.dataGroup = state.get(STATE_DATA_GROUP) as DataGroup
            }
            if (state.containsKey(STATE_DATE_TIMESTAMP) && state.containsKey(STATE_TIME_TIMESTAMP)) {
                this.dateCalendar.timeInMillis = state.getLong(STATE_DATE_TIMESTAMP)
                this.timeCalendar.timeInMillis = state.getLong(STATE_TIME_TIMESTAMP)
            }
        } else {
            this.dataGroup = Prefs.lastDataGroup(this)
        }
    }

    private fun setDataGroup(dataGroup: DataGroup, force: Boolean = false) {
        if (dataGroup != this.dataGroup || force) {
            this.dataGroup = dataGroup
            Prefs.setLastDataGroup(this, dataGroup)
            val existingFragment = fragmentManager.findFragmentByTag("ROOT")
            if (existingFragment?.javaClass != dataGroup.fragmentClass || force) {
                fragmentManager.beginTransaction()
                        .replace(R.id.content, dataGroup.fragmentClass.newInstance(), "ROOT")
                        .commit()
            }
        }
    }

    fun setToolbarTitle(title: Int) {
        toolbar?.setTitle(title)
    }

    fun setToolbarSubtitle(subtitle: String? = null) {
        toolbar?.subtitle = subtitle
    }

    fun setToolbarSubtitle(subtitle: Int) {
        toolbar?.setSubtitle(subtitle)
    }

    private fun initNavigationDrawer() {
        navigationView.setCheckedItem(R.id.dayDetail)
        navigationView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawers()
            when (menuItem.itemId) {
                R.id.dayDetail -> setDataGroup(DataGroup.DAY_DETAIL)
                R.id.tracker -> setDataGroup(DataGroup.TRACKER)
                R.id.calendars -> setDataGroup(DataGroup.MONTH_CALENDARS)
                R.id.yearEvents -> setDataGroup(DataGroup.YEAR_EVENTS)
            }
            true
        }
        val actionBarDrawerToggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close)
        drawerLayout.addDrawerListener(actionBarDrawerToggle)
        actionBarDrawerToggle.syncState()
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    companion object {
        private const val STATE_DATA_GROUP = "dataView"
        private const val STATE_DATE_TIMESTAMP = "dateTimestamp"
        private const val STATE_TIME_TIMESTAMP = "timeTimestamp"

        private const val DATA_TAG = "dataFragment"

        private val TAG = DataActivity::class.java.simpleName
    }
}