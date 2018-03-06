package uk.co.sundroid.activity.data.fragments

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.view.View
import uk.co.sundroid.R
import uk.co.sundroid.util.astro.Body
import uk.co.sundroid.util.astro.RiseSetType
import uk.co.sundroid.util.astro.YearData
import uk.co.sundroid.util.astro.math.SunCalculator
import uk.co.sundroid.util.geometry.*
import uk.co.sundroid.util.theme.*
import uk.co.sundroid.util.astro.YearData.Event
import uk.co.sundroid.util.astro.YearData.EventType
import uk.co.sundroid.util.time.*

import java.util.Calendar
import java.util.TreeSet

import uk.co.sundroid.util.time.formatDurationHMS

import kotlinx.android.synthetic.main.frag_data_daydetail_sun.*
import uk.co.sundroid.activity.MainActivity

class DayDetailSunFragment : AbstractDayFragment() {

    private val handler = Handler()

    override val layout: Int
        get() = R.layout.frag_data_daydetail_sun

    override fun onResume() {
        super.onResume()
        (activity as MainActivity).setToolbarTitle(R.string.data_detail_title)
        (activity as MainActivity).setToolbarSubtitle(R.string.data_detail_subtitle_sun)
    }

    override fun updateData(view: View) {
        val location = getLocation()
        val calendar = getDateCalendar()

        val thread = object : Thread() {
            override fun run() {
                if (!isSafe) {
                    return
                }

                val yearEvents = YearData.getYearEvents(calendar.get(Calendar.YEAR), calendar.timeZone)
                val yearEventToday: Event? = yearEvents.lastOrNull { it.type.body === Body.SUN && isSameDay(calendar, it.time) }
                val sunDay = SunCalculator.calcDay(location.location, calendar)

                handler.post {
                    if (isSafe) {
                        if (yearEventToday != null) {
                            view.findViewById<View>(R.id.sunEvent).setOnClickListener(null)
                            showInView(view, R.id.sunEvent)
                            showInView(view, R.id.sunEventTitle, yearEventToday.type.displayName)
                            if (yearEventToday.type === EventType.NORTHERN_SOLSTICE && Math.abs(location.location.latitude.doubleValue) > 23.44) {
                                val localExtreme = if (location.location.latitude.doubleValue >= 0) "Longest" else "Shortest"
                                showInView(view, R.id.sunEventSubtitle, localExtreme + " day")
                            } else if (yearEventToday.type === EventType.SOUTHERN_SOLSTICE && Math.abs(location.location.latitude.doubleValue) > 23.44) {
                                val localExtreme = if (location.location.latitude.doubleValue >= 0) "Shortest" else "Longest"
                                showInView(view, R.id.sunEventSubtitle, localExtreme + " day")
                            } else if (yearEventToday.type === EventType.ANNULAR_SOLAR || yearEventToday.type === EventType.HYBRID_SOLAR || yearEventToday.type === EventType.PARTIAL_SOLAR || yearEventToday.type === EventType.TOTAL_SOLAR) {
                                showInView(view, R.id.sunEventSubtitle, "Tap to check Wikipedia for visibility")
                                val finalLink = yearEventToday.link
                                view.findViewById<View>(R.id.sunEvent).setOnClickListener {
                                    val intent = Intent(Intent.ACTION_VIEW)
                                    intent.data = Uri.parse(finalLink)
                                    startActivity(intent)
                                }
                            } else {
                                removeInView(view, R.id.sunEventSubtitle)
                            }
                        } else {
                            removeInView(view, R.id.sunEvent)
                        }

                        var noTransit = true
                        var noUptime = true

                        if (sunDay.riseSetType !== RiseSetType.SET && sunDay.transitAppElevation > 0) {
                            val noon = formatTime(applicationContext!!, sunDay.transit!!, false)
                            noTransit = false
                            showInView(view, R.id.sunTransit)
                            showInView(view, R.id.sunTransitTime, noon.time + noon.marker + "  " + formatElevation(sunDay.transitAppElevation))
                        } else {
                            removeInView(view, R.id.sunTransit)
                        }

                        if (sunDay.riseSetType === RiseSetType.RISEN || sunDay.riseSetType === RiseSetType.SET) {
                            showInView(view, R.id.sunSpecial, if (sunDay.riseSetType === RiseSetType.RISEN) "Risen all day" else "Set all day")
                            removeInView(view, R.id.sunEvtsRow, R.id.sunEvt1, R.id.sunEvt2, R.id.sunUptime)
                        } else {
                            removeInView(view, R.id.sunSpecial)
                            removeInView(view, R.id.sunEvt1, R.id.sunEvt2)
                            showInView(view, R.id.sunEvtsRow)
                            val events = TreeSet<SummaryEvent>()
                            if (sunDay.rise != null) {
                                events.add(SummaryEvent("Rise", sunDay.rise!!, sunDay.riseAzimuth))
                            }
                            if (sunDay.set != null) {
                                events.add(SummaryEvent("Set", sunDay.set!!, sunDay.setAzimuth))
                            }
                            var index = 1
                            for (event in events) {
                                val rowId = view("sunEvt" + index)
                                val timeId = view("sunEvt" + index + "Time")
                                val azId = view("sunEvt" + index + "Az")
                                val imgId = view("sunEvt" + index + "Img")

                                val time = formatTime(applicationContext!!, event.time, false)
                                val az = formatBearing(applicationContext!!, event.azimuth!!, location.location, event.time)

                                textInView(view, timeId, time.time + time.marker)
                                textInView(view, azId, az)
                                showInView(view, rowId)
                                imageInView(view, imgId, if (event.name == "Rise") getRiseArrow() else getSetArrow())

                                index++
                            }

                            if (sunDay.uptimeHours > 0 && sunDay.uptimeHours < 24) {
                                noUptime = false
                                showInView(view, R.id.sunUptime)
                                showInView(view, R.id.sunUptimeTime, formatDurationHMS(applicationContext!!, sunDay.uptimeHours, false))
                            } else {
                                removeInView(view, R.id.sunUptime)
                            }

                        }

                        if (noTransit && noUptime) {
                            removeInView(view, R.id.sunTransitUptime, R.id.sunTransitUptimeDivider)
                        } else {
                            showInView(view, R.id.sunTransitUptime, R.id.sunTransitUptimeDivider)
                        }

                        sunDay.apply {
                            hashMapOf(
                                    sunCivDawnTime to civDawn,
                                    sunCivDuskTime to civDusk,
                                    sunNtcDawnTime to ntcDawn,
                                    sunNtcDuskTime to ntcDusk,
                                    sunAstDawnTime to astDawn,
                                    sunAstDuskTime to astDusk
                            ).forEach {
                                (view, time) -> view.text = time?.let { formatTimeStr(activity, it) } ?: "-"
                            }
                        }
                        showInView(view, R.id.sunDataBox)
                    }
                }
            }
        }
        thread.start()
    }


}
