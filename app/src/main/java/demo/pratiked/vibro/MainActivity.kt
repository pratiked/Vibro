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
import demo.pratiked.vibro.models.Audio
import demo.pratiked.vibro.services.MediaPlayerService
import android.provider.MediaStore
import android.support.v7.widget.LinearLayoutManager
import demo.pratiked.vibro.adapters.AudioAdapter
import kotlinx.android.synthetic.main.activity_main.*
import demo.pratiked.vibro.utils.StorageUtil




class MainActivity : AppCompatActivity() {

    val BROADCAST_PLAY_NEW_AUDIO = "demo.pratiked.vibro.PlayNewAudio"

    companion object {
        private const val TAG = "MainActivity"
    }

    private var player: MediaPlayerService? = null
    private var serviceBound = false
    private var audioList = ArrayList<Audio>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.i(TAG, "onCreate")

        //playAudio("https://upload.wikimedia.org/wikipedia/commons/6/6c/Grieg_Lyric_Pieces_Kobold.ogg")

        getLocalAudio()
        Log.i(TAG, "Local audios: " + audioList.size)

        rv_audios.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        rv_audios.adapter = AudioAdapter(audioList)

        /*if (audioList.size > 0){
            playAudio(audioList[0].data!!)
        }*/

    }

    override fun onDestroy() {
        super.onDestroy()

        if (serviceBound) {
            unbindService(serviceConnection);
            player!!.stopSelf()
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {

        outState!!.putBoolean("ServiceState", serviceBound)

        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)

        serviceBound = savedInstanceState!!.getBoolean("ServiceState")
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

    private fun playAudio(audioIndex: Int) {
        //Check is service is active
        if (!serviceBound) {
            //Store Serializable audioList to SharedPreferences
            val storage = StorageUtil(applicationContext)
            storage.storeAudio(audioList)
            storage.storeAudioIndex(audioIndex)

            val playerIntent = Intent(this, MediaPlayerService::class.java)
            startService(playerIntent)
            bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        } else {
            //Store the new audioIndex to SharedPreferences
            val storage = StorageUtil(applicationContext)
            storage.storeAudioIndex(audioIndex)

            //Service is active
            //Send a broadcast to the service -> PLAY_NEW_AUDIO
            val broadcastIntent = Intent(BROADCAST_PLAY_NEW_AUDIO)
            sendBroadcast(broadcastIntent)
        }
    }

    /*private fun playAudio(media: String) {
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
    }*/

    private fun getLocalAudio() {

        val contentResolver = contentResolver

        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0"
        val sortOrder = MediaStore.Audio.Media.TITLE + " ASC"
        val cursor = contentResolver.query(uri, null, selection, null, sortOrder)

        if (cursor != null && cursor.count > 0) {
            audioList = ArrayList()
            while (cursor.moveToNext()) {

                val isMusic = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC))
                //1 - true and 0 - false
                if (isMusic != null && isMusic == "1"){

                    val data = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
                    val title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
                    val album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))
                    val artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))
                    val composer = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.COMPOSER))
                    val displayName = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME))
                    val duration = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION))
                    val mimeType = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE))
                    val size = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.SIZE))

                    Log.i(TAG, "" + data)
                    Log.i(TAG, "" + title)
                    Log.i(TAG, "" + album)
                    Log.i(TAG, "" + artist)
                    Log.i(TAG, "" + composer)
                    Log.i(TAG, "" + displayName)
                    Log.i(TAG, "" + duration)
                    Log.i(TAG, "" + mimeType)
                    Log.i(TAG, "" + size)

                    // Save to audioList
                    audioList.add(Audio(data, title, album, artist, composer, displayName, duration, mimeType, size))
                }
            }
        }
        cursor!!.close()
    }

}
