package uk.co.sundroid.util.astro.image

import uk.co.sundroid.util.log.*
import uk.co.sundroid.util.prefs.Prefs
import uk.co.sundroid.util.astro.Body
import uk.co.sundroid.util.astro.math.BodyPositionCalculator
import uk.co.sundroid.util.location.LatitudeLongitude
import uk.co.sundroid.util.geometry.*
import uk.co.sundroid.util.theme.*
import uk.co.sundroid.util.time.*

import android.content.Context
import android.graphics.*
import android.graphics.Paint.Cap
import android.graphics.Paint.Style
import android.view.View
import uk.co.sundroid.domain.MapType
import uk.co.sundroid.util.astro.BodyDayEvent
import uk.co.sundroid.util.astro.math.SunCalculator
import java.util.*
import kotlin.math.*
import uk.co.sundroid.util.astro.BodyDayEvent.Direction.*
import uk.co.sundroid.util.astro.BodyDayEvent.Event.*


class TrackerImage(val style: TrackerStyle, val context: Context, val location: LatitudeLongitude) {

    companion object {
        val TAG: String = TrackerImage::class.java.name
    }

    class TrackerStyle(val cardinals: Int, val circles: Int, val day: Int, val golden: Int,
                       val civ: Int, val ntc: Int, val ast: Int,
                       val night: Int, val nightLine: Int,
                       val bodyRisen: Int, val bodySet: Int, val stroke: Int, val markerStroke: Int, val dash: Array<Float>, val isRadar: Boolean) {

        companion object {

            private val NORMAL_MAP: TrackerStyle = TrackerStyle(
                    Color.argb(255, 0, 0, 0),
                    Color.argb(100, 0, 0, 0),
                    Color.argb(255, 255, 204, 0),
                    Color.argb(255, 255, 157, 0),
                    Color.argb(255, 99, 116, 166),
                    Color.argb(255, 72, 90, 144),
                    Color.argb(255, 47, 65, 119),
                    Color.argb(255, 26, 41, 88),
                    Color.argb(255, 26, 41, 88),
                    Color.argb(255, 255, 204, 0),
                    Color.argb(255, 72, 90, 144),
                    3,
                    2,
                    arrayOf(4f, 6f),
                    false
            )
            private val SATELLITE_MAP: TrackerStyle = TrackerStyle(
                    Color.argb(255, 255, 255, 255),
                    Color.argb(150, 255, 255, 255),
                    Color.argb(255, 255, 204, 0),
                    Color.argb(255, 255, 157, 0),
                    Color.argb(255, 99, 116, 166),
                    Color.argb(255, 72, 90, 144),
                    Color.argb(255, 47, 65, 119),
                    Color.argb(255, 26, 41, 88),
                    Color.argb(255, 26, 41, 88),
                    Color.argb(255, 255, 204, 0),
                    Color.argb(255, 72, 90, 144),
                    3,
                    2,
                    arrayOf(4f, 6f),
                    false
            )

            fun forMode(mode: String, mapType: MapType): TrackerStyle {
                if (mode == "radar") {
                    return getTrackerRadarStyle()
                } else if (mapType == MapType.NORMAL || mapType == MapType.TERRAIN) {
                    return NORMAL_MAP
                } else if (mapType == MapType.SATELLITE || mapType == MapType.HYBRID) {
                    return SATELLITE_MAP
                }
                return getTrackerRadarStyle()
            }
        }
    }
    
    private val body: Body? = Prefs.sunTrackerBody(context)
    private val hourMarkers: Boolean = Prefs.sunTrackerHourMarkers(context)
    private val linearElevation: Boolean = Prefs.sunTrackerLinearElevation(context)
    private val magneticBearings: Boolean = Prefs.magneticBearings(context)
    private var magneticDeclination = 0.0
    
    private var dateCalendar = Calendar.getInstance()
    private var timeCalendar = Calendar.getInstance()
    
    private var dateBitmap: Bitmap? = null
    private var timeBitmap: Bitmap? = null
    
    private var dateBitmapTimestamp = 0L
    private var timeBitmapTimestamp = 0L
    
    private var containerWidth = 0
    private var containerHeight = 0

    fun setDate(dateCalendar: Calendar, timeCalendar: Calendar) {
        this.dateCalendar = clone(dateCalendar)
        this.timeCalendar = clone(timeCalendar)
        this.magneticDeclination = getMagneticDeclination(this.location, this.dateCalendar)
    }
    
    fun setTime(timeCalendar: Calendar) {
        this.timeCalendar = clone(timeCalendar)
    }
    
    fun drawOnCanvas(container: View, canvas: Canvas, left: Float, top: Float) {
        this.containerWidth = container.width
        this.containerHeight = container.height
        val timeBitmap = updateTimeImageIfStale(clone(timeCalendar))
        val dateBitmap = updateDateImageIfStale(clone(dateCalendar))
        val paint = Paint()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        canvas.drawBitmap(dateBitmap, left - (dateBitmap.width/2f), top - (dateBitmap.height/2f), paint)
        canvas.drawBitmap(timeBitmap, left - (timeBitmap.width/2f), top - (timeBitmap.height/2f), paint)
    }
    
    fun generate() {
        if (containerWidth == 0 || containerHeight == 0) {
            d(TAG, "Cannot generate, dimensions unknown")
            return
        }
        try {
            updateTimeImageIfStale(clone(timeCalendar))
            updateDateImageIfStale(clone(dateCalendar))
        } catch (t: Throwable) {
            e(TAG, "Generate failed: ", t)
        }
    }
    
    private fun createBitmap(): Bitmap {
        val size = min(containerWidth, containerHeight)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    }
    
    private fun updateTimeImageIfStale(timeCalendar: Calendar): Bitmap {
        val currentBitmap = timeBitmap
        if (currentBitmap != null && timeBitmapTimestamp == timeCalendar.timeInMillis) {
            return currentBitmap
        }
        val bitmap = createBitmap()
        
        val padding = size(25)
        val size = bitmap.width
        val outerRadius = (size - (2 * padding))/2
        val centerX = (size/2f)
        val centerY = (size/2f)

        val canvas = Canvas(bitmap)
        val paint = Paint()

        paint.isAntiAlias = true
        
        if (body == null) {
            
            for (body in Body.values()) {
                
                paint.style = Style.FILL
                
                val position = BodyPositionCalculator.calcPosition(body, location, timeCalendar)
                
                if (position.appElevation >= 0) {
                
                    val apparentRadius = if (linearElevation) (outerRadius - ((abs(position.appElevation) / 90.0) * outerRadius)).toFloat() else (cos(degToRad(position.appElevation)) * outerRadius).toFloat()
                    val x = (sin(degToRad(position.azimuth)) * apparentRadius).toFloat()
                    val y = (cos(degToRad(position.azimuth)) * apparentRadius).toFloat()
                    
                    if (!style.isRadar) {
                        paint.color = Color.argb(100, 0, 0, 0)
                        canvas.drawCircle(centerX + x, centerY - y, size(6), paint)
                        
                        val strokePaint = Paint()
                        strokePaint.color = Color.argb(100, 0, 0, 0)
                        strokePaint.textSize = size(14)
                        strokePaint.style = Style.STROKE
                        strokePaint.strokeWidth = size(2)
                        strokePaint.isAntiAlias = true
                        canvas.drawText(body.name.substring(0, 1) + body.name.substring(1).toLowerCase(Locale.getDefault()), centerX + x + size(6), centerY - y + size(5), strokePaint)
                    }
                        
                    paint.strokeWidth = size(style.stroke)
                    paint.color = getBodyColor(body)
                    canvas.drawCircle(centerX + x, centerY - y, size(4), paint)
                    
                    
                    val textPaint = Paint()
                    textPaint.textSize = size(14)
                    textPaint.color = getBodyColor(body)
                    textPaint.isAntiAlias = true
                    canvas.drawText(body.name.substring(0, 1) + body.name.substring(1).toLowerCase(Locale.getDefault()), centerX + x + size(6), centerY - y + size(5), textPaint)
                }
            }

            
        } else {
            
            paint.style = Style.FILL
            
            val position = BodyPositionCalculator.calcPosition(body, location, timeCalendar)

            val apparentRadius = if (linearElevation) (outerRadius - ((abs(position.appElevation)/90.0) * outerRadius)).toFloat() else (cos(degToRad(position.appElevation)) * outerRadius).toFloat()
            val x = (sin(degToRad(position.azimuth)) * apparentRadius).toFloat()
            val y = (cos(degToRad(position.azimuth)) * apparentRadius).toFloat()
            
            if (!style.isRadar) {
                paint.strokeWidth = size(style.stroke + 1)
                paint.color = Color.argb(100, 0, 0, 0)
                canvas.drawLine(centerX, centerY, centerX + x, centerY - y, paint)
                canvas.drawCircle(centerX + x, centerY - y, size(7), paint)
            }
                
            paint.strokeWidth = size(style.stroke)
            val lineColor = getElevationColor(position.appElevation, true)
            val bodyColor = getElevationColor(90.0, true)
            paint.color = lineColor
            canvas.drawLine(centerX, centerY, centerX + x, centerY - y, paint)
            paint.color = appBackground()
            if (style.isRadar) {
                canvas.drawCircle(centerX + x, centerY - y, size(8), paint)
            } else {
                canvas.drawCircle(centerX + x, centerY - y, size(7), paint)
            }
            paint.color = bodyColor
            canvas.drawCircle(centerX + x, centerY - y, size(6), paint)
            
        }
        
        synchronized (this) {
            timeBitmap = bitmap
            timeBitmapTimestamp = timeCalendar.timeInMillis
        }
        return bitmap
    }
    
    private fun updateDateImageIfStale(dateCalendar: Calendar): Bitmap {
        val currentBitmap = dateBitmap
        if (currentBitmap != null && dateBitmapTimestamp == dateCalendar.timeInMillis) {
            return currentBitmap
        }
        val bitmap = createBitmap()
        
        val padding = size(25)
        val size = bitmap.width.toFloat()
        val outerRadius = (size - (2 * padding))/2
        val fontSize = size(14)
        val centerX = (size/2f)
        val centerY = (size/2f)
        
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.color = style.circles
        paint.style = Style.STROKE
        paint.isAntiAlias = true
        paint.strokeWidth = size(1)

        (0 until 90 step 15)
                .map { if (linearElevation) (outerRadius - ((abs(it)/90.0) * outerRadius)).toFloat() else (cos(degToRad(it.toDouble())) * outerRadius).toFloat() }
                .forEach { canvas.drawCircle(centerX, centerY, it, paint) }
        
        if (magneticBearings) {
            canvas.rotate(magneticDeclination.toFloat(), centerX, centerY)
        }
        
        val textPaint = Paint()
        textPaint.isAntiAlias = true
        textPaint.textSize = fontSize
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = false
        textPaint.typeface = Typeface.DEFAULT
        textPaint.color = style.cardinals
        
        canvas.drawText(if (magneticBearings) "N(M)" else "N(T)", (size/2), size(12), textPaint)
        canvas.drawText("S", size/2, size - size(4), textPaint)
        canvas.drawText("E", size - size(10), size/2 + size(5), textPaint)
        canvas.drawText("W", size(10), size/2 + size(5), textPaint)
        
        paint.color = style.circles
        for (az in 0 until 360 step 45) {
            val x = (sin(degToRad(az.toDouble())) * outerRadius).toFloat()
            val y = (cos(degToRad(az.toDouble())) * outerRadius).toFloat()
            canvas.drawLine(centerX, centerY, centerX + x, centerY - y, paint)
        }
        
        if (magneticBearings) {
            canvas.rotate(-magneticDeclination.toFloat(), centerX, centerY)
        }
        
        if (body != null) {

            paint.strokeWidth = size(style.stroke)
            
            val loopCalendar = clone(dateCalendar)
            
            var path = Path()

            var position = BodyPositionCalculator.calcPosition(body, location, loopCalendar)
            var apparentRadius = if (linearElevation) (outerRadius - ((abs(position.appElevation)/90.0) * outerRadius)).toFloat() else (cos(degToRad(position.appElevation)) * outerRadius).toFloat()
            var x = (sin(degToRad(position.azimuth)) * apparentRadius).toFloat()
            var y = (cos(degToRad(position.azimuth)) * apparentRadius).toFloat()
            var currentColor = getElevationColor(position.appElevation, false)
            paint.color = currentColor
            path.moveTo(centerX + x, centerY - y)
            loopCalendar.add(Calendar.MINUTE, 10)
            
            // Get rise/set events that happen on this calendar day, midnight to midnight. Positions
            // will be calculated at these times as well as 10 minute intervals, to allow accurate
            // colour changes.
            val eventsSet = TreeSet<BodyDayEvent>()
            val riseTimes = TreeSet<Long>()
            val setTimes = TreeSet<Long>()
            val dayLoopCalendar = clone(dateCalendar)
            dayLoopCalendar.add(Calendar.DAY_OF_MONTH, -1)

            for (i in 0 until 3) {
                if (body == Body.SUN) {
                    val day = SunCalculator.calcDay(location, dayLoopCalendar, RISESET, CIVIL, NAUTICAL, ASTRONOMICAL, GOLDENHOUR)
                    eventsSet.addAll(day.events.filter { e -> isSameDay(e.time, dateCalendar) })
                } else {
                    val day = BodyPositionCalculator.calcDay(body, location, dayLoopCalendar, false)
                    eventsSet.addAll(day.events.filter { e -> isSameDay(e.time, dateCalendar) })
                }
                dayLoopCalendar.add(Calendar.DAY_OF_MONTH, 1)
            }
            riseTimes.addAll(eventsSet.filter { e -> e.direction == RISING }.map { e -> e.time.timeInMillis })
            setTimes.addAll(eventsSet.filter { e -> e.direction == DESCENDING }.map { e -> e.time.timeInMillis })

            // Determine calculated times in advance so sunrise and sunset can be inserted in order.
            val calcTimes = TreeSet<Long>()
            calcTimes.add(loopCalendar.timeInMillis)
            do {
                loopCalendar.add(Calendar.MINUTE, 10)
                calcTimes.add(loopCalendar.timeInMillis)
            } while (loopCalendar.get(Calendar.DAY_OF_YEAR) == dateCalendar.get(Calendar.DAY_OF_YEAR))
            eventsSet.mapTo(calcTimes) { it.time.timeInMillis }

            var prevX = x
            var prevY = y
            
            val paths = LinkedHashMap<Path, Int>()
            
            for (calcTime in calcTimes) {
                loopCalendar.timeInMillis = calcTime
                position = BodyPositionCalculator.calcPosition(body, location, loopCalendar)
                apparentRadius = if (linearElevation) (outerRadius - ((abs(position.appElevation)/90.0) * outerRadius)).toFloat() else (cos(degToRad(position.appElevation)) * outerRadius).toFloat()
                x = (sin(degToRad(position.azimuth)) * apparentRadius).toFloat()
                y = (cos(degToRad(position.azimuth)) * apparentRadius).toFloat()

                var nudge = 0.0
                if (calcTime in riseTimes) {
                    nudge = 0.1
                } else if (calcTime in setTimes) {
                    nudge = -0.1
                }
                val thisColor = getElevationColor(position.appElevation + nudge, false)

                if (loopCalendar.get(Calendar.MINUTE) == 0 && hourMarkers) {
                    // Draw lines across the path at a tangent to the line from the previous
                    // point. Could improve slightly by averaging next point as well.
                    val dx = x - prevX
                    val dy = y - prevY
                    val angle = atan(dy.toDouble()/dx.toDouble())
                    val inverse = (Math.PI/2) - angle
                    val markY = (size(5) * sin(inverse)).toFloat()
                    val markX = (size(5) * cos(inverse)).toFloat()
                    
                    val cap = paint.strokeCap
                    
                    paint.strokeCap = Cap.ROUND
                    if (!style.isRadar) {
                        val color = paint.color
                        paint.color = Color.argb(100, 0, 0, 0)
                        paint.strokeWidth = size(style.markerStroke + 1)
                        canvas.drawLine((centerX + x) + markX, (centerY - y) + markY, (centerX + x) - markX, (centerY - y) - markY, paint)
                        paint.color = color
                    }

                    paint.strokeWidth = size(style.markerStroke)
                    canvas.drawLine((centerX + x) + markX, (centerY - y) + markY, (centerX + x) - markX, (centerY - y) - markY, paint)
                    paint.strokeWidth = size(style.stroke)
                    
                    paint.strokeCap = cap
                    
                }
                
                if (thisColor != currentColor) {
                    path.lineTo(centerX + x, centerY - y)
                    paint.alpha = 50
                    paint.maskFilter = BlurMaskFilter(size(style.stroke * 6), BlurMaskFilter.Blur.NORMAL)
                    paint.strokeWidth = size(style.stroke * 8)
                    canvas.drawPath(path, paint)
                    paint.alpha = 255
                    paint.maskFilter = null
                    paint.strokeWidth = size(style.stroke)
                    canvas.drawPath(path, paint)
                    paths[path] = currentColor
                    currentColor = thisColor
                    paint.color = currentColor
                    path = Path()
                    path.moveTo(centerX + x, centerY - y)
                } else {
                    path.lineTo(centerX + x, centerY - y)
                }
                
                prevX = x
                prevY = y
            }

            paths[path] = currentColor
            
            if (!style.isRadar) {
                paint.strokeWidth = size(style.stroke + 1)
                paint.color = Color.argb(100, 0, 0, 0)
                paths.keys.forEach { canvas.drawPath(it, paint) }
            }
            
            paint.strokeWidth = size(style.stroke)
            paths.forEach { (path, color) ->
                paint.color = color
                canvas.drawPath(path, paint)
            }
    
            // Draw dotted lines for rise and set.
            
            paint.pathEffect = DashPathEffect(style.dash.toFloatArray(), 0F)
            
            for (event in eventsSet.filter { e -> e.event == RISESET }) {
                x = (sin(degToRad(event.azimuth!!)) * outerRadius).toFloat()
                y = (cos(degToRad(event.azimuth)) * outerRadius).toFloat()
                
                if (!style.isRadar) {
                    paint.pathEffect = null
                    paint.strokeWidth = size(style.stroke + 1)
                    paint.color = Color.argb(50, 0, 0, 0)
                    canvas.drawLine(centerX, centerY, centerX + x, centerY - y, paint)
                }
                
                paint.pathEffect = DashPathEffect(style.dash.toFloatArray(), 0F)
                paint.strokeWidth = size(style.stroke)
                paint.color = if (body == Body.SUN) style.golden else style.bodyRisen
                canvas.drawLine(centerX, centerY, centerX + x, centerY - y, paint)
            }
        }

        synchronized (this) {
            dateBitmap = bitmap
            dateBitmapTimestamp = dateCalendar.timeInMillis
        }
        return bitmap
        
    }

    fun size(size: Int): Float {
        val metrics = context.resources.displayMetrics
        return ((metrics.densityDpi/160.0) * size).toInt().toFloat()
    }
    
    private fun getElevationColor(elevation: Double, line: Boolean): Int {
        when (body) {
            Body.SUN -> {
                return when {
                    elevation < -18 -> if (line) style.nightLine else style.night
                    elevation < -12 -> style.ast
                    elevation < -6 -> style.ntc
                    elevation <= -0.833 -> style.civ
                    elevation < 6 -> style.golden
                    else -> return style.day
                }
            }
            Body.MOON -> {
                return when {
                    elevation >= -0.5 -> style.bodyRisen
                    else -> style.bodySet
                }
            }
            else -> {
                return when {
                    elevation >= 0.0 -> style.bodyRisen
                    else -> style.bodySet
                }
            }
        }
    }
    
    private fun degToRad(angleDeg: Double): Double {
        return (Math.PI * angleDeg / 180.0)
    }
    
}