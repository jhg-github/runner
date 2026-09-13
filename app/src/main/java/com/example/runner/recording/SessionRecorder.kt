package com.example.runner.recording

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Holds the points of one running session in RAM, periodically flushes a backup GPX
 * to the app cache, and exports the final GPX to the user's Downloads folder.
 */
class SessionRecorder {
    private val points = mutableListOf<TrackPoint>()

    private var startTime: Instant = Instant.now()

    /** Total paused time during the session, deducted from elapsed. */
    private var accumulatedPausedMs: Long = 0

    /** Non-null only while the session is paused. */
    private var pauseStart: Instant? = null

    /** Clears any previous session data. Called when entering WAITING_TO_START. */
    fun startNewSession() {
        points.clear()
        startTime = Instant.now()
        accumulatedPausedMs = 0
        pauseStart = null
    }

    /** Marks the pause instant. Idempotent. */
    fun onPause() {
        if (pauseStart == null) pauseStart = Instant.now()
    }

    /** Folds the paused span into accumulatedPausedMs. Idempotent. */
    fun onResume() {
        pauseStart?.let { accumulatedPausedMs += Duration.between(it, Instant.now()).toMillis() }
        pauseStart = null
    }

    /**
     * Time since the session started, excluding paused spans. While paused the value is
     * frozen at the pause instant.
     */
    fun elapsedMillis(): Long {
        val base = pauseStart ?: Instant.now()
        return maxOf(0L, Duration.between(startTime, base).toMillis() - accumulatedPausedMs)
    }

    /** Appends one sample taken during RECORDING. */
    fun addPoint(point: TrackPoint) {
        points.add(point)
    }

    fun pointCount(): Int = points.size

    /**
     * Writes the current session as GPX to [cacheDir]/session_backup.gpx, overwriting the
     * previous backup. Called every 30 s while recording so a crash loses at most 30 s of data.
     */
    fun flushToBackup(cacheDir: File) {
        cacheDir.resolve(BACKUP_FILE).writeText(generateGpxXml())
    }

    /** Deletes the backup file from the cache. */
    fun deleteBackup(cacheDir: File) {
        cacheDir.resolve(BACKUP_FILE).delete()
    }

    /**
     * Saves the full session as a uniquely named GPX file (date-based) into Downloads and
     * clears the session. Returns the content [Uri].
     */
    fun saveToDownloads(context: Context): Uri? {
        val fileName = "Run_" + DateTimeFormatter.ofPattern(FILE_NAME_PATTERN)
            .withZone(ZoneId.systemDefault()).format(Instant.now()) + ".gpx"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/gpx+xml")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
        )
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { out ->
                out.write(generateGpxXml().toByteArray())
            }
        }
        points.clear()
        return uri
    }

    /** Builds the GPX 1.1 doc, mirroring the format of docs/Night_Run.gpx. */
    fun generateGpxXml(): String {
        val timeFmt = DateTimeFormatter.ISO_INSTANT
        val sb = StringBuilder()
        sb.append(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<gpx creator=\"Runner\" version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd " +
                "http://www.garmin.com/xmlschemas/GpxExtensions/v3 http://www.garmin.com/xmlschemas/GpxExtensionsv3.xsd " +
                "http://www.garmin.com/xmlschemas/TrackPointExtension/v1 http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd\" " +
                "xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\" " +
                "xmlns:gpxx=\"http://www.garmin.com/xmlschemas/GpxExtensions/v3\">\n"
        )
        val sessionStart = startTime
        sb.append(" <metadata>\n  <time>${iso(sessionStart, timeFmt)}</time>\n </metadata>\n")
        sb.append(" <trk>\n  <name>Run</name>\n  <type>running</type>\n  <trkseg>\n")
        for (p in points) {
            sb.append("   <trkpt lat=\"${"%.7f".format(p.latitude)}\" lon=\"${"%.7f".format(p.longitude)}\">\n")
            sb.append("    <ele>${"%.1f".format(p.elevation)}</ele>\n")
            sb.append("    <time>${iso(p.timestamp, timeFmt)}</time>\n")
            if (p.heartRate > 0) {
                sb.append("    <extensions>\n     <gpxtpx:TrackPointExtension>\n")
                sb.append("      <gpxtpx:hr>${p.heartRate}</gpxtpx:hr>\n")
                sb.append("     </gpxtpx:TrackPointExtension>\n    </extensions>\n")
            }
            sb.append("   </trkpt>\n")
        }
        sb.append("  </trkseg>\n </trk>\n</gpx>\n")
        return sb.toString()
    }

    private fun iso(instant: Instant, fmt: DateTimeFormatter): String = fmt.format(instant)

    companion object {
        const val BACKUP_FILE = "session_backup.gpx"
        private const val FILE_NAME_PATTERN = "yyyy-MM-dd_HH-mm-ss"
    }
}