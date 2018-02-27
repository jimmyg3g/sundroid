package uk.co.sundroid.activity.data.fragments

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import uk.co.sundroid.R
import uk.co.sundroid.util.astro.Body
import uk.co.sundroid.util.astro.MoonDay
import uk.co.sundroid.util.astro.MoonPhase
import uk.co.sundroid.util.astro.MoonPhaseEvent
import uk.co.sundroid.util.astro.YearData
import uk.co.sundroid.util.astro.image.MoonPhaseImage
import uk.co.sundroid.util.astro.RiseSetType
import uk.co.sundroid.util.astro.math.BodyPositionCalculator
import uk.co.sundroid.util.astro.math.MoonPhaseCalculator
import uk.co.sundroid.domain.LocationDetails
import uk.co.sundroid.util.geometry.*
import uk.co.sundroid.util.log.*
import uk.co.sundroid.util.theme.*
import uk.co.sundroid.util.time.*
import uk.co.sundroid.util.time.Time
import uk.co.sundroid.util.astro.YearData.Event

import java.util.*

class DayDetailMoonFragment : AbstractDayFragment() {

    private val handler = Handler()

    protected override val layout: Int
        get() = R.layout.frag_data_daydetail_moon

    @Throws(Exception::class)
    override fun update(location: LocationDetails, calendar: Calendar, view: View) {

        val thread = object : Thread() {
            override fun run() {
                if (!isSafe) {
                    return
                }

                val moonDay = BodyPositionCalculator.calcDay(Body.MOON, location.location, calendar, true) as MoonDay
                val moonPhaseEvents = ArrayList<MoonPhaseEvent>()
                for (event in MoonPhaseCalculator.getYearEvents(calendar.get(Calendar.YEAR), calendar.timeZone)) {
                    if (event.time.get(Calendar.DAY_OF_YEAR) >= calendar.get(Calendar.DAY_OF_YEAR)) {
                        moonPhaseEvents.add(event)
                    }
                }
                if (moonPhaseEvents.size < 4) {
                    moonPhaseEvents.addAll(MoonPhaseCalculator.getYearEvents(calendar.get(Calendar.YEAR) + 1, calendar.timeZone))
                }

                val yearEvents = YearData.getYearEvents(calendar.get(Calendar.YEAR), calendar.timeZone)
                var yearEventToday: Event? = null
                for (yearEvent in yearEvents) {
                    if (yearEvent.type.body === Body.MOON) {
                        if (isSameDay(calendar, yearEvent.time)) {
                            yearEventToday = yearEvent
                        }
                    }
                }
                val todayEvent = yearEventToday

                // Asynchronously generate moon graphic to speed up response.
                val moonThread = object : Thread() {
                    override fun run() {
                        if (!isSafe) {
                            return
                        }

                        try {
                            val start = System.currentTimeMillis()
                            val phase = moonDay.phaseDouble
                            val moonBitmap = MoonPhaseImage.makeImage(resources, R.drawable.moon, phase, location.location.latitude.doubleValue < 0, MoonPhaseImage.SIZE_LARGE)
                            val end = System.currentTimeMillis()
                            handler.post {
                                if (isSafe) {
                                    val moon = view.findViewById<ImageView>(R.id.moonImage)
                                    moon.setImageBitmap(moonBitmap)
                                }
                            }
                            d(TAG, "Moon render time " + (end - start))
                        } catch (e: Exception) {
                            e(TAG, "Error generating moon", e)
                        }

                    }
                }
                moonThread.start()

                handler.post {
                    if (isSafe) {
                        if (todayEvent != null) {
                            view.findViewById<View>(R.id.moonEvent).setOnClickListener(null)
                            showInView(view, R.id.moonEvent)
                            showInView(view, R.id.moonEventTitle, todayEvent.type.displayName)
                            showInView(view, R.id.moonEventSubtitle, "Tap to check Wikipedia for visibility")
                            val finalLink = todayEvent.link
                            view.findViewById<View>(R.id.moonEvent).setOnClickListener { view1 ->
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.data = Uri.parse(finalLink)
                                startActivity(intent)
                            }
                        } else {
                            removeInView(view, R.id.moonEvent)
                        }

                        for (i in 1..4) {
                            val phaseEvent = moonPhaseEvents[i - 1]
                            val phaseImgView = view("moonPhase" + i + "Img")
                            val phaseLabelView = view("moonPhase" + i + "Label")
                            var phaseImg = getPhaseFull()
                            if (phaseEvent.phase === MoonPhase.NEW) {
                                phaseImg = getPhaseNew()
                            } else if (phaseEvent.phase === MoonPhase.FIRST_QUARTER) {
                                phaseImg = if (location.location.latitude.doubleValue >= 0) getPhaseRight() else getPhaseLeft()
                            } else if (phaseEvent.phase === MoonPhase.LAST_QUARTER) {
                                phaseImg = if (location.location.latitude.doubleValue >= 0) getPhaseLeft() else getPhaseRight()
                            }
                            (view.findViewById<View>(phaseImgView) as ImageView).setImageResource(phaseImg)
                            (view.findViewById<View>(phaseLabelView) as TextView).text = shortDateAndMonth(phaseEvent.time)
                        }

                        var noTransit = true
                        var noUptime = true

                        if (moonDay.riseSetType !== RiseSetType.SET && moonDay.transitAppElevation > 0) {
                            val noon = formatTime(applicationContext!!, moonDay.transit!!, false)
                            noTransit = false
                            showInView(view, R.id.moonTransit)
                            showInView(view, R.id.moonTransitTime, noon.time + noon.marker + "  " + formatElevation(moonDay.transitAppElevation))
                        } else {
                            removeInView(view, R.id.moonTransit)
                        }

                        if (moonDay.riseSetType === RiseSetType.RISEN || moonDay.riseSetType === RiseSetType.SET) {
                            showInView(view, R.id.moonSpecial, if (moonDay.riseSetType === RiseSetType.RISEN) "Risen all day" else "Set all day")
                            removeInView(view, R.id.moonEvtsRow, R.id.moonEvt1, R.id.moonEvt2, R.id.moonUptime)
                        } else {
                            removeInView(view, R.id.moonSpecial)
                            removeInView(view, R.id.moonEvt1, R.id.moonEvt2)
                            showInView(view, R.id.moonEvtsRow)
                            val events = TreeSet<SummaryEvent>()
                            if (moonDay.rise != null) {
                                events.add(SummaryEvent("Rise", moonDay.rise!!, moonDay.riseAzimuth))
                            }
                            if (moonDay.set != null) {
                                events.add(SummaryEvent("Set", moonDay.set!!, moonDay.setAzimuth))
                            }
                            var index = 1
                            for (event in events) {
                                val rowId = view("moonEvt" + index)
                                val timeId = view("moonEvt" + index + "Time")
                                val azId = view("moonEvt" + index + "Az")
                                val imgId = view("moonEvt" + index + "Img")

                                val time = formatTime(applicationContext!!, event.time, false)
                                val az = formatBearing(applicationContext!!, event.azimuth!!, location.location, event.time)

                                textInView(view, timeId, time.time + time.marker)
                                textInView(view, azId, az)
                                showInView(view, rowId)
                                imageInView(view, imgId, if (event.name == "Rise") getRiseArrow() else getSetArrow())

                                index++
                            }

                            if (moonDay.uptimeHours > 0 && moonDay.uptimeHours < 24) {
                                noUptime = false
                                showInView(view, R.id.moonUptime)
                                showInView(view, R.id.moonUptimeTime, formatDurationHMS(applicationContext!!, moonDay.uptimeHours, false))
                            } else {
                                removeInView(view, R.id.moonUptime)
                            }

                        }

                        if (noTransit && noUptime) {
                            removeInView(view, R.id.moonTransitUptime, R.id.moonTransitUptimeDivider)
                        } else {
                            showInView(view, R.id.moonTransitUptime, R.id.moonTransitUptimeDivider)
                        }

                        if (moonDay.phaseEvent == null) {
                            showInView(view, R.id.moonPhase, moonDay.phase!!.displayName)
                        } else {
                            val time = formatTime(applicationContext!!, moonDay.phaseEvent!!.time, false)
                            showInView(view, R.id.moonPhase, moonDay.phase!!.displayName + " at " + time.time + time.marker)
                        }
                        showInView(view, R.id.moonIllumination, Integer.toString(moonDay.illumination) + "%")
                        showInView(view, R.id.moonDataBox)
                    }
                }
            }
        }
        thread.start()

    }

    companion object {
        private val TAG = DayDetailMoonFragment::class.java.simpleName
    }

}