package demo.pratiked.vibro

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import demo.pratiked.vibro.services.MediaPlayerService



class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var player: MediaPlayerService? = null
    private var serviceBound = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.i(TAG, "onCreate")

        playAudio("https://upload.wikimedia.org/wikipedia/commons/6/6c/Grieg_Lyric_Pieces_Kobold.ogg")
    }

    override fun onDestroy() {
        super.onDestroy()

        unbindService(serviceConnection)
    }

    //Binding this Client to the AudioPlayer Service
    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as MediaPlayerService.LocalBinder
            player = binder.service
            serviceBound = true

            Toast.makeText(this@MainActivity, "Service Bound", Toast.LENGTH_SHORT).show()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            serviceBound = false
        }
    }

    private fun playAudio(media: String) {
        //Check is service is active
        if (!serviceBound) {
            Log.i(TAG, "service is not active")
            val playerIntent = Intent(this, MediaPlayerService::class.java)
            playerIntent.putExtra("media", media)
            startService(playerIntent)
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        } else {
            Log.i(TAG, "service is active")
            //Service is active
            //Send media with BroadcastReceiver
        }
    }

}
