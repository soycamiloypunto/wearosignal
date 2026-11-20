package com.wearosignal.app

import android.app.Application
import com.wearosignal.app.poll.MaintenanceWorker
import com.wearosignal.app.poll.PollScheduler
import com.wearosignal.app.util.AndroidLogger
import org.signal.core.util.logging.Log

class WearSignalApp : Application() {
  override fun onCreate() {
    super.onCreate()
    Log.initialize(AndroidLogger())
    AppDeps.init(this)
    PollScheduler.scheduleNext(this)
    MaintenanceWorker.ensureScheduled(this)
  }
}
