package demo.pratiked.vibro.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.io.IOException
import android.telephony.TelephonyManager
import android.telephony.PhoneStateListener
import demo.pratiked.vibro.models.Audio
import demo.pratiked.vibro.MainActivity
import demo.pratiked.vibro.utils.StorageUtil
import android.media.session.MediaController.TransportControls
import android.media.session.MediaSessionManager
import android.os.RemoteException
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import demo.pratiked.vibro.R


class MediaPlayerService: /*extends*/ Service(), /*implements*/ MediaPlayer.OnCompletionListener,
    MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
    MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnInfoListener,
    MediaPlayer.OnBufferingUpdateListener, AudioManager.OnAudioFocusChangeListener{

    companion object {
        private const val TAG = "MediaPlayerService"
    }

    val ACTION_PLAY = "demo.pratiked.vibro.services.audioplayer.ACTION_PLAY"
    val ACTION_PAUSE = "demo.pratiked.vibro.services.audioplayer.ACTION_PAUSE"
    val ACTION_PREVIOUS = "demo.pratiked.vibro.services.audioplayer.ACTION_PREVIOUS"
    val ACTION_NEXT = "demo.pratiked.vibro.services.audioplayer.ACTION_NEXT"
    val ACTION_STOP = "demo.pratiked.vibro.services.audioplayer.ACTION_STOP"

    //List of available Audio files
    private val audioList: ArrayList<Audio>? = null
    private var audioIndex = -1
    private var activeAudio: Audio? = null //an object of the currently playing audio


    private val iBinder = LocalBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var mediaFile: String? = null
    private var resumePosition: Int = 0
    private var audioManager: AudioManager? = null
    private var mAudioFocusRequest: AudioFocusRequest? = null
    private var ongoingCall = false
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyManager: TelephonyManager? = null

    //MediaSession
    private var mediaSessionManager: MediaSessionManager? = null
    private var mediaSession: MediaSessionCompat? = null
    private var transportControls: MediaControllerCompat.TransportControls? = null

    //AudioPlayer notification ID
    private val NOTIFICATION_ID = 101

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        Log.i(TAG, "onStartCommand")

        //An audio file is passed to the service through putExtra()
        try {
            mediaFile = intent!!.getStringExtra("media")
        } catch (e: NullPointerException){
            Log.e(TAG, "NullPointerException: ", e)
            stopSelf()
        }

        //Request audio focus
        if (!requestAudioFocus()){
            Log.i(TAG, "no audio focus")
            //Could not gain focus
            stopSelf()
        }

        if (mediaFile != null && !mediaFile.equals("")){
            initMediaPlayer()
        } else {
            Log.i(TAG, "file link is null")
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()

        // Perform one-time setup procedures

        // Manage incoming phone calls during playback.
        // Pause MediaPlayer on incoming call,
        // Resume on hangup.
        callStateListener()
        //ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs -- BroadcastReceiver
        registerBecomingNoisyReceiver()
        //Listen for new Audio to play -- BroadcastReceiver
        registerPlayNewAudio()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (mediaPlayer != null){
            stopMedia()
            mediaPlayer!!.release()
        }

        //todo
        //removeAudioFocus()

        //Disable the PhoneStateListener
        if (phoneStateListener != null) {
            telephonyManager!!.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        }

        //todo
        //removeNotification()

        //unregister BroadcastReceivers
        unregisterReceiver(becomingNoisyReceiver)
        unregisterReceiver(playNewAudio)

        //clear cached playlist
        StorageUtil(applicationContext).clearCachedAudioPlaylist()
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.i(TAG, "onBind")
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

        Log.i(TAG, "onAudioFocusChange")

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
        Log.i(TAG, "onPrepared")
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

        Log.i(TAG, "initMediaPlayer")

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

    private val becomingNoisyReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            //pause audio on ACTION_AUDIO_BECOMING_NOISY
            pauseMedia()
            //buildNotification(PlaybackStatus.PAUSED);
        }
    }

    private fun registerBecomingNoisyReceiver() {
        //register after getting audio focus
        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(becomingNoisyReceiver, intentFilter)
    }

    //Handle incoming phone calls
    private fun callStateListener() {

        // Get the telephony manager
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        //Starting listening for PhoneState changes
        phoneStateListener = object : PhoneStateListener() {

            override fun onCallStateChanged(state: Int, incomingNumber: String) {
                when (state) {
                    //if at least one call exists or the phone is ringing
                    //pause the MediaPlayer
                    TelephonyManager.CALL_STATE_OFFHOOK, TelephonyManager.CALL_STATE_RINGING -> if (mediaPlayer != null) {
                        pauseMedia()
                        ongoingCall = true
                    }
                    TelephonyManager.CALL_STATE_IDLE ->
                        // Phone idle. Start playing.
                        if (mediaPlayer != null) {
                            if (ongoingCall) {
                                ongoingCall = false
                                resumeMedia()
                            }
                        }
                }
            }
        }
        // Register the listener with the telephony manager
        // Listen for changes to the device call state.
        telephonyManager!!.listen(
            phoneStateListener,
            PhoneStateListener.LISTEN_CALL_STATE
        )
    }

    private val playNewAudio = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            //Get the new media index form SharedPreferences
            audioIndex = StorageUtil(applicationContext).loadAudioIndex()

            if (audioIndex != -1 && audioIndex < audioList!!.size) {
                //index is in a valid range
                activeAudio = audioList[audioIndex]
            } else {
                stopSelf()
            }

            //A PLAY_NEW_AUDIO action received
            //reset mediaPlayer to play the new Audio
            stopMedia()
            mediaPlayer!!.reset()
            initMediaPlayer()
            //todo
            //updateMetaData()
            //buildNotification(PlaybackStatus.PLAYING)
        }
    }

    private fun registerPlayNewAudio() {
        //Register playNewMedia receiver
        val filter = IntentFilter(MainActivity.BROADCAST_PLAY_NEW_AUDIO)
        registerReceiver(playNewAudio, filter)
    }


    @Throws(RemoteException::class)
    private fun initMediaSession() {
        if (mediaSessionManager != null) return  //mediaSessionManager exists

        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        // Create a new MediaSession
        mediaSession = MediaSessionCompat(applicationContext, "AudioPlayer")
        //Get MediaSessions transport controls
        transportControls = mediaSession!!.controller.transportControls
        //set MediaSession -> ready to receive media commands
        mediaSession!!.isActive = true
        //indicate that the MediaSession handles transport control commands
        // through its MediaSessionCompat.Callback.
        mediaSession!!.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

        //Set mediaSession's MetaData
        updateMetaData()

        // Attach Callback to receive MediaSession updates
        mediaSession!!.setCallback(object : MediaSessionCompat.Callback() {
            // Implement callbacks
            override fun onPlay() {
                super.onPlay()
                resumeMedia()
                //todo
                //buildNotification(PlaybackStatus.PLAYING);
            }

            override fun onPause() {
                super.onPause()
                pauseMedia()
                //todo
                //buildNotification(PlaybackStatus.PAUSED);
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                //todo
                //skipToNext();
                updateMetaData()
                //todo
                //buildNotification(PlaybackStatus.PLAYING);
            }

            override fun onSkipToPrevious() {
                super.onSkipToPrevious()
                //todo
                //skipToPrevious();
                updateMetaData()
                //todo
                //buildNotification(PlaybackStatus.PLAYING);
            }

            override fun onStop() {
                super.onStop()
                //todo
                //removeNotification();
                //Stop the service
                stopSelf()
            }

            override fun onSeekTo(position: Long) {
                super.onSeekTo(position)
            }
        })
    }

    private fun updateMetaData() {
        val albumArt = BitmapFactory.decodeResource(
            resources,
            R.drawable.image
        ) //replace with medias albumArt

        // Update the current metadata
        mediaSession!!.setMetadata(
            MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, activeAudio!!.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, activeAudio!!.album)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, activeAudio!!.title)
                .build()
        )
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