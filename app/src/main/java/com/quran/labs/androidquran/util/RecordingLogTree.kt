package com.quran.labs.androidquran.util

import android.util.Log
import com.quran.analytics.provider.SystemCrashReporter
import timber.log.Timber
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException
import java.util.ArrayDeque
import java.util.Deque
import javax.net.ssl.SSLException

/**
 * A logging implementation which buffers the last 200 messages.
 * It uploads them to Crashlytics upon the logging of an error.
 *
 * Slightly modified version of Telecine's BugsnagTree.
 * https://github.com/JakeWharton/Telecine
 */
class RecordingLogTree : Timber.Tree() {

  companion object {
    private const val BUFFER_SIZE = 200

    private fun priorityToString(priority: Int): String {
      return when (priority) {
        Log.ERROR -> "E"
        Log.WARN -> "W"
        Log.INFO -> "I"
        Log.DEBUG -> "D"
        else -> priority.toString()
      }
    }

    // offline devices, flaky DNS and cancelled requests aren't bugs, but they made up
    // most of the non-fatals in Crashlytics
    private fun Throwable.isNetworkError(): Boolean {
      return generateSequence(this) { it.cause }.take(5).any {
        it is UnknownHostException || it is ConnectException || it is SocketException ||
          it is InterruptedIOException || it is SSLException
      }
    }
  }

  private val crashlytics = SystemCrashReporter.crashReporter()

  // Adding one to the initial size accounts for the add before remove.
  private val buffer: Deque<String> = ArrayDeque(BUFFER_SIZE + 1)

  override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
    val newMessage = System.currentTimeMillis().toString() + " " + priorityToString(priority) + " " + message
    synchronized(buffer) {
      buffer.addLast(newMessage)
      if (buffer.size > BUFFER_SIZE) {
        buffer.removeFirst()
      }
    }

    if (t != null && priority == Log.ERROR && !t.isNetworkError()) {
      crashlytics.log(getLogs())
      crashlytics.recordException(t)
    }
  }

  fun getLogs(): String {
    val builder = StringBuilder()
    synchronized(buffer) {
      for (message in buffer) {
        builder.append(message).append(".\n")
      }
    }
    return builder.toString()
  }
}
