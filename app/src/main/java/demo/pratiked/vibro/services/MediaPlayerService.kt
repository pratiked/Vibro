package demo.pratiked.vibro.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.*
import android.media.session.MediaController
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
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.os.RemoteException
import demo.pratiked.vibro.R
import demo.pratiked.vibro.utils.Constants


class MediaPlayerService: /*extends*/ Service(), /*implements*/ MediaPlayer.OnCompletionListener,
    MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
    MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnInfoListener,
    MediaPlayer.OnBufferingUpdateListener, AudioManager.OnAudioFocusChangeListener{

    companion object {
        private const val TAG = "MediaPlayerService"
    }

    private val ACTION_PLAY = "demo.pratiked.vibro.services.audioplayer.ACTION_PLAY"
    private val ACTION_PAUSE = "demo.pratiked.vibro.services.audioplayer.ACTION_PAUSE"
    private val ACTION_PREVIOUS = "demo.pratiked.vibro.services.audioplayer.ACTION_PREVIOUS"
    private val ACTION_NEXT = "demo.pratiked.vibro.services.audioplayer.ACTION_NEXT"
    private val ACTION_STOP = "demo.pratiked.vibro.services.audioplayer.ACTION_STOP"

    //List of available Audio files
    private var audioList: ArrayList<Audio>? = null
    private var audioIndex = -1
    private var activeAudio: Audio? = null //an object of the currently playing audio


    private val iBinder = LocalBinder()
    private var mediaPlayer: MediaPlayer? = null
    //private var mediaFile: String? = null
    private var resumePosition: Int = 0
    private var audioManager: AudioManager? = null
    private var mAudioFocusRequest: AudioFocusRequest? = null
    private var ongoingCall = false
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyManager: TelephonyManager? = null

    //MediaSession
    private var mediaSessionManager: MediaSessionManager? = null
    private var mediaSession: MediaSession? = null
    private var transportControls: MediaController.TransportControls? = null

    //AudioPlayer notification ID
    private val NOTIFICATION_ID = 101

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        Log.i(TAG, "onStartCommand")

        //An audio file is passed to the service through putExtra()
        try {
            //mediaFile = intent!!.getStringExtra("media")

            //Load data from SharedPreferences
            val storage = StorageUtil(applicationContext)
            audioList = storage.loadAudio()
            audioIndex = storage.loadAudioIndex()

            if (audioIndex != -1 && audioIndex < audioList!!.size) {
                //index is in a valid range
                activeAudio = audioList!![audioIndex]
            } else {
                stopSelf()
            }
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

        /*if (mediaFile != null && !mediaFile.equals("")){
            initMediaPlayer()
        } else {
            Log.i(TAG, "file link is null")
        }*/

        if (mediaSessionManager == null){
            try {
                initMediaSession()
                initMediaPlayer()
            } catch (e: RemoteException) {
                e.printStackTrace()
                stopSelf()
            }
            buildNotification(Constants.PlaybackStatus.PLAYING)
        }

        //Handle Intent action from MediaSession.TransportControls
        handleIncomingActions(intent)

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

        removeAudioFocus()

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
            mediaPlayer!!.setDataSource(activeAudio!!.data)
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

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
                activeAudio = audioList!![audioIndex]
            } else {
                stopSelf()
            }

            //A PLAY_NEW_AUDIO action received
            //reset mediaPlayer to play the new Audio
            stopMedia()
            mediaPlayer!!.reset()
            initMediaPlayer()

            updateMetaData()
            buildNotification(Constants.PlaybackStatus.PLAYING)
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
        mediaSession = MediaSession(applicationContext, "AudioPlayer")
        //Get MediaSessions transport controls
        transportControls = mediaSession!!.controller.transportControls
        //set MediaSession -> ready to receive media commands
        mediaSession!!.isActive = true
        //indicate that the MediaSession handles transport control commands
        // through its MediaSessionCompat.Callback.
        mediaSession!!.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)

        //Set mediaSession's MetaData
        updateMetaData()

        // Attach Callback to receive MediaSession updates
        mediaSession!!.setCallback(object : MediaSession.Callback() {
            // Implement callbacks
            override fun onPlay() {
                super.onPlay()
                resumeMedia()
                buildNotification(Constants.PlaybackStatus.PLAYING)
            }

            override fun onPause() {
                super.onPause()
                pauseMedia()
                buildNotification(Constants.PlaybackStatus.PAUSED)
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                skipToNext()
                updateMetaData()
                buildNotification(Constants.PlaybackStatus.PLAYING)
            }

            override fun onSkipToPrevious() {
                super.onSkipToPrevious()

                skipToPrevious()
                updateMetaData()
                buildNotification(Constants.PlaybackStatus.PLAYING)
            }

            override fun onStop() {
                super.onStop()
                //todo
                //removeNotification()
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
            MediaMetadata.Builder()
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, activeAudio!!.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, activeAudio!!.album)
                .putString(MediaMetadata.METADATA_KEY_TITLE, activeAudio!!.title)
                .build()
        )
    }


    private fun skipToNext() {

        if (audioIndex == audioList!!.size - 1) {
            //if last in playlist
            audioIndex = 0
            activeAudio = audioList!![audioIndex]
        } else {
            //get next in playlist
            activeAudio = audioList!![++audioIndex]
        }

        //Update stored index
        StorageUtil(applicationContext).storeAudioIndex(audioIndex)

        stopMedia()
        //reset mediaPlayer
        mediaPlayer!!.reset()
        initMediaPlayer()
    }

    private fun skipToPrevious() {

        if (audioIndex == 0) {
            //if first in playlist
            //set index to the last of audioList
            audioIndex = audioList!!.size - 1
            activeAudio = audioList!![audioIndex]
        } else {
            //get previous in playlist
            activeAudio = audioList!![--audioIndex]
        }

        //Update stored index
        StorageUtil(applicationContext).storeAudioIndex(audioIndex)

        stopMedia()
        //reset mediaPlayer
        mediaPlayer!!.reset()
        initMediaPlayer()
    }

    private fun buildNotification(playbackStatus: Constants.PlaybackStatus) {

        var notificationAction = android.R.drawable.ic_media_pause//needs to be initialized
        var playPauseAction: PendingIntent? = null

        //Build a new notification according to the current state of the MediaPlayer
        if (playbackStatus === Constants.PlaybackStatus.PLAYING) {
            notificationAction = android.R.drawable.ic_media_pause
            //create the pause action
            playPauseAction = playbackAction(1)
        } else if (playbackStatus === Constants.PlaybackStatus.PAUSED) {
            notificationAction = android.R.drawable.ic_media_play
            //create the play action
            playPauseAction = playbackAction(0)
        }

        val largeIcon = BitmapFactory.decodeResource(
            resources,
            R.drawable.image
        ) //replace with your own image

        // Create a new Notification
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channelId = "General"

            val actionPrevious: Notification.Action =
                Notification.Action.Builder(android.R.drawable.ic_media_previous, "previous",
                    playbackAction(3)).build()

            val actionPause: Notification.Action =
                Notification.Action.Builder(notificationAction, "pause", playPauseAction).build()

            val actionNext: Notification.Action =
                Notification.Action.Builder(android.R.drawable.ic_media_next, "next",
                    playbackAction(2)).build()

            Notification.Builder(this@MediaPlayerService, channelId)
                .setShowWhen(false)
                // Set the Notification style
                .setStyle(
                    Notification.MediaStyle()
                        // Attach our MediaSession token
                        .setMediaSession(mediaSession!!.sessionToken)
                        // Show our playback controls in the compact notification view.
                        .setShowActionsInCompactView(0, 1, 2)
                )
                // Set the Notification color
                .setColor(getColor(R.color.colorPrimary))
                // Set the large and small icons
                .setLargeIcon(largeIcon)
                .setSmallIcon(android.R.drawable.stat_sys_headset)
                // Set Notification content information
                .setContentText(activeAudio!!.artist)
                .setContentTitle(activeAudio!!.album)
                .setChannelId(channelId)

                // Add playback actions
                .addAction(actionPrevious)
                .addAction(actionPause)
                .addAction(actionNext) as Notification.Builder
        } else {
            TODO("VERSION.SDK_INT < O")
        }

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notificationBuilder.build())
    }


    private fun handleIncomingActions(playbackAction: Intent?) {
        if (playbackAction == null || playbackAction.action == null) return

        val actionString = playbackAction.action
        when {
            actionString!!.equals(ACTION_PLAY, ignoreCase = true) -> transportControls!!.play()
            actionString.equals(ACTION_PAUSE, ignoreCase = true) -> transportControls!!.pause()
            actionString.equals(ACTION_NEXT, ignoreCase = true) -> transportControls!!.skipToNext()
            actionString.equals(ACTION_PREVIOUS, ignoreCase = true) -> transportControls!!.skipToPrevious()
            actionString.equals(ACTION_STOP, ignoreCase = true) -> transportControls!!.stop()
        }
    }

    private fun playbackAction(actionNumber: Int): PendingIntent? {
        val playbackAction = Intent(this, MediaPlayerService::class.java)
        when (actionNumber) {
            0 -> {
                // Play
                playbackAction.action = ACTION_PLAY
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            1 -> {
                // Pause
                playbackAction.action = ACTION_PAUSE
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            2 -> {
                // Next track
                playbackAction.action = ACTION_NEXT
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            3 -> {
                // Previous track
                playbackAction.action = ACTION_PREVIOUS
                return PendingIntent.getService(this, actionNumber, playbackAction, 0)
            }
            else -> {
            }
        }
        return null
    }

    inner class LocalBinder : Binder() {

        val service: MediaPlayerService
            get() = this@MediaPlayerService
    }
}