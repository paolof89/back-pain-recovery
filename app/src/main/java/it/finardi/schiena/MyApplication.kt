package it.finardi.schiena

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import it.finardi.schiena.notif.DebugStore
import it.finardi.schiena.notif.NotificationCoordinator
import javax.inject.Inject

@HiltAndroidApp
class MyApplication : Application() {
	@Inject lateinit var debugStore: DebugStore
	@Inject lateinit var notificationCoordinator: NotificationCoordinator

	override fun onCreate() {
		super.onCreate()
		debugStore.installCrashHandler()
		notificationCoordinator.start()
	}
}