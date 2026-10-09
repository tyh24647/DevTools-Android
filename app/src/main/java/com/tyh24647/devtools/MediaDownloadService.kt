package com.tyh24647.devtools
import android.app.*
import android.content.Intent
import android.os.IBinder
class MediaDownloadService: Service() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("media-downloads","Media downloads and recordings",NotificationManager.IMPORTANCE_LOW))
        val intent=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        startForeground(18748,Notification.Builder(this,"media-downloads").setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("DevTools media transfer").setContentText("Open Resources to view progress or stop recording.")
            .setContentIntent(intent).setOngoing(true).build())
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        val library=MediaLibrary.get(this)
        library.tasks.firstOrNull {it.id==intent?.getStringExtra("task")}?.let(library::launch)
        if(library.tasks.none {!it.done})stopSelf()
        return START_NOT_STICKY
    }
    override fun onTimeout(startId:Int,fgsType:Int){MediaLibrary.get(this).tasks.filter {!it.done}.forEach {it.stopRequested=true;if(!it.choice.live)it.job?.cancel()};stopSelf()}
    override fun onBind(intent:Intent?):IBinder?=null
}
