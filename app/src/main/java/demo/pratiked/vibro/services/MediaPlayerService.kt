package demo.pratiked.vibro.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.io.IOException

class MediaPlayerService: /*extends*/ Service(), /*implements*/ MediaPlayer.OnCompletionListener,
    MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
    MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnInfoListener,
    MediaPlayer.OnBufferingUpdateListener, AudioManager.OnAudioFocusChangeListener{

    companion object {
        private const val TAG = "Demo"
    }

    private val iBinder = LocalBinder()
    private var mediaPlayer: MediaPlayer? = null
    private val mediaFile: String? = null
    private var resumePosition: Int = 0
    private var audioManager: AudioManager? = null
    private var mAudioFocusRequest: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? {
        return iBinder
    }


    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {

        when(what){
            MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK -> {
                Log.e(TAG, "Not valid for progressive playback")
            }
            MediaPlayer.MEDIA_ERROR_SERVER_DIED -> {
                Log.e(TAG, "Server died")
            }
            MediaPlayer.MEDIA_ERROR_UNKNOWN -> {
                Log.e(TAG, "Unknown error")
            }
        }
        return false
    }

    override fun onSeekComplete(mp: MediaPlayer?) {

    }

    override fun onInfo(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        return false
    }

    override fun onBufferingUpdate(mp: MediaPlayer?, percent: Int) {

    }

    override fun onAudioFocusChange(focusChange: Int) {

        /*For a good user experience with audio in Android, it is important that the app plays
        nicely with the system and other apps that also play media*/

        when(focusChange){
            AudioManager.AUDIOFOCUS_GAIN -> {

                //resume playback
                if (mediaPlayer == null){
                    initMediaPlayer()
                } else if (!mediaPlayer!!.isPlaying){
                    mediaPlayer!!.start()
                    mediaPlayer!!.setVolume(1.0f, 1.0f)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {

                //Lost focus for an unbounded amount of time: stop playback and release media player
                if (mediaPlayer!!.isPlaying){
                    mediaPlayer!!.stop()
                    mediaPlayer!!.release()
                    mediaPlayer = null
                }
            }
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> {

                /*Lost focus for a short time, but we have to stop
                 playback. We don't release the media player because playback
                 is likely to resume*/

                if (mediaPlayer!!.isPlaying){
                    mediaPlayer!!.pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {

                //Lost focus for a short time, but it's ok to keep playing at an attenuated level
                if (mediaPlayer!!.isPlaying){
                    mediaPlayer!!.setVolume(0.1f, 0.1f)
                }
            }
        }
    }

    override fun onPrepared(mp: MediaPlayer?) {
        playMedia()
    }

    override fun onCompletion(mp: MediaPlayer?) {
        stopMedia()
        //stop the service
        stopSelf()
    }

    /*
    ***JAVA***
    private void initMediaPlayer() {
        mediaPlayer = new MediaPlayer();

        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnInfoListener(this);

        mediaPlayer.reset();

        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
        try {
            mediaPlayer.setDataSource(mediaFile);
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }
        mediaPlayer.prepareAsync();
    }
    */

    private fun initMediaPlayer() {

        mediaPlayer = MediaPlayer()

        //Set up MediaPlayer event listeners
        mediaPlayer!!.setOnCompletionListener(this)
        mediaPlayer!!.setOnErrorListener(this)
        mediaPlayer!!.setOnPreparedListener(this)
        mediaPlayer!!.setOnBufferingUpdateListener(this)
        mediaPlayer!!.setOnSeekCompleteListener(this)
        mediaPlayer!!.setOnInfoListener(this)

        //Reset so that the MediaPlayer is not pointing to another data source
        mediaPlayer!!.reset()

        mediaPlayer!!.setAudioAttributes(AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build())

        try {
            // Set the data source to the mediaFile location
            mediaPlayer!!.setDataSource(mediaFile)
        } catch (e: IOException) {
            e.printStackTrace()
            stopSelf()
        }

        mediaPlayer!!.prepareAsync()
    }

    private fun playMedia() {
        if (!mediaPlayer!!.isPlaying) {
            mediaPlayer!!.start()
        }
    }

    private fun stopMedia() {
        if (mediaPlayer == null)
            return
        if (mediaPlayer!!.isPlaying) {
            mediaPlayer!!.stop()
        }
    }

    private fun pauseMedia() {
        if (mediaPlayer!!.isPlaying) {
            mediaPlayer!!.pause()
            resumePosition = mediaPlayer!!.currentPosition
        }
    }

    private fun resumeMedia() {
        if (!mediaPlayer!!.isPlaying) {
            mediaPlayer!!.seekTo(resumePosition)
            mediaPlayer!!.start()
        }
    }

    private fun requestAudioFocus():Boolean{

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            mAudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this).build()

            val focusRequest = audioManager!!.requestAudioFocus(mAudioFocusRequest)

            if (focusRequest == AudioManager.AUDIOFOCUS_REQUEST_GRANTED){
                return true
            }
        }

        return false
    }

    private fun removeAudioFocus():Boolean {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager!!.abandonAudioFocusRequest(mAudioFocusRequest)
        } else {
            return false
        }
    }

    /*
    ***JAVA***
    public class LocalBinder extends Binder {

      public MediaPlayerService getService() {
          return MediaPlayerService.this;
      }
    }
    */
    inner class LocalBinder : Binder() {

        val service: MediaPlayerService
            get() = this@MediaPlayerService
    }
}