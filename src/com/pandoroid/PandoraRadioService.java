/* Pandoroid Radio - open source pandora.com client for android
 * Copyright (C) 2011  Andrew Regner <andrew@aregner.com>
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.pandoroid;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;

import com.pandoroid.pandora.PandoraRadio;
import com.pandoroid.pandora.RPCException;
import com.pandoroid.pandora.Song;
import com.pandoroid.pandora.Station;
import com.pandoroid.pandora.SubscriberTypeException;
import com.pandoroid.playback.MediaPlaybackController;
import com.pandoroid.playback.OnNewSongListener;
import com.pandoroid.playback.OnPlaybackContinuedListener;
import com.pandoroid.playback.OnPlaybackHaltedListener;
import com.pandoroid.R;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.os.Build;
import android.os.PowerManager;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.app.NotificationCompat.MediaStyle;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.support.v4.media.session.PlaybackStateCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import static android.content.ContentValues.TAG;
import static android.support.v4.app.NotificationManagerCompat.IMPORTANCE_MIN;

/**
 * Description: Someone really needs to give this class some loving, document
 *  it up, organize it, and make it thread safe.
 */
public class PandoraRadioService extends Service {

    private static final int NOTIFICATION_SONG_PLAYING = 1;
    

    
    // tools this service uses
    private PandoraRadio m_pandora_remote;
    public MediaPlaybackController m_song_playback;
    public ImageDownloader image_downloader;
    
    private TelephonyManager telephonyManager;
    private ConnectivityManager connectivity_manager;
    private MusicIntentReceiver m_music_intent_receiver;
    private SharedPreferences m_prefs;
    
    // tracking/organizing what we are doing
    private Station m_current_station;
    private String m_audio_quality;
    private boolean m_paused;
    
    //We'll use this for now as the database implementation is garbage.
    private ArrayList<Station> m_stations; 
    private HashMap<Class<?>,Object> listeners = new HashMap<Class<?>,Object>();

    protected PandoraDB db;

    
    // static usefullness
    private static Object lock = new Object();
    private static Object pandora_lock = new Object();

    private MediaSessionCompat.Token mSessionToken;
    public static final String ACTION_PAUSE = "action_pause";
    public static final String ACTION_PLAY = "action_play";
    public static final String ACTION_NEXT = "action_next";
    private MediaPlayer mMediaPlayer;
    private MediaSessionManager mManager;
    private MediaSession mSession;
    private MediaController mController;
    private PowerManager.WakeLock mWakeLock;

    //Taken straight from the Android service reference
    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class PandoraRadioBinder extends Binder {
        PandoraRadioService getService() {
            return PandoraRadioService.this;
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // This is the object that receives interactions from clients. 
    private final IBinder mBinder = new PandoraRadioBinder();
    //End service reference
    
    
    @Override
    public void onCreate() {
        m_paused = false;
        m_pandora_remote = new PandoraRadio();
        image_downloader = new ImageDownloader();
        m_stations = new ArrayList<Station>();
        
        
        connectivity_manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        m_prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        //final MediaSession mediaSession = new MediaSession(this, "pandoroid session");

        MediaPlayer m_player = new MediaPlayer();
        //m_player.setWakeMode(getApplicationContext() , PowerManager.PARTIAL_WAKE_LOCK);
        m_player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        //mediaSession.setActive(true);
        //mediaSession.setCallback(new MediaSession.Callback() {
        //    public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
        //        Log.d(TAG, "onMediaButtonEvent called: " + mediaButtonIntent);
        //        return false;
        //    }

        //    public void onPause() {
        //        Log.d(TAG, "onPause called (media button pressed)");
        //        //super.onPause();
        //        m_song_playback.pause();
        //    }

        //    public void onPlay() {
        //        Log.d(TAG, "onPlay called (media button pressed)");
        //        //super.onPlay();
        //        m_song_playback.play();
        //    }

        //    public void onStop() {
        //        Log.d(TAG, "onStop called (media button pressed)");
        //        //super.onStop();
        //        m_song_playback.skip();
        //    }
        //});
        //mediaSession.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "PandoroidWakelock");
        mWakeLock.acquire();

        // Register the listener with the telephony manager
        telephonyManager.listen(new PhoneStateListener() {
            boolean pausedForRing = false;
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                Log.i("pandoroid telephony", "State changed: " + state);
                switch(state) {

                case TelephonyManager.CALL_STATE_IDLE:
                    Log.d("DEBUG", "***********IDLE********");
                    if(pausedForRing && m_song_playback != null) {
                        if(m_prefs.getBoolean("behave_resumeOnHangup", true)) {
                            if(m_song_playback != null && !m_paused){
                                m_song_playback.play();
                            }
                        }
                    }

                    pausedForRing = false;
                    break;

                case TelephonyManager.CALL_STATE_OFFHOOK:
                case TelephonyManager.CALL_STATE_RINGING:
                    Log.d("DEBUG", "***********RINGING********");
                    if(m_song_playback != null) {
                        m_song_playback.pause();
                    }

                    pausedForRing = true;
                    break;
                }
            }
        }, PhoneStateListener.LISTEN_CALL_STATE);
        
        m_music_intent_receiver = new MusicIntentReceiver();
        this.registerReceiver(m_music_intent_receiver, 
                              new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
    
    public void onDestroy() {
        if (m_song_playback != null){
            m_song_playback.stop();
        }
        this.unregisterReceiver(m_music_intent_receiver);
        stopForeground(true);
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(001);
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        Intent resultIntent = new Intent(this, PandoraRadioService.class);
        onTaskRemoved(resultIntent);
        return;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(001);
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }
    
    public Song getCurrentSong() throws Exception{
        return m_song_playback.getSong();
    }
    
    public Station getCurrentStation() {
        return m_current_station;
    }
    
    public ArrayList<Station> getStations(){
        return m_stations;
    }
    
    public boolean isPartnerAuthorized(){
        return m_pandora_remote.isPartnerAuthorized();
    }
    
    public boolean isUserAuthorized(){
        return m_pandora_remote.isUserAuthorized();
    }
    
    public void runPartnerLogin(boolean pandora_one_subscriber_flag) throws RPCException, 
                                                                            IOException,
                                                                            HttpResponseException,
                                                                            Exception{
        Log.i("Pandoroid", 
              "Running a partner login for a " +
              (pandora_one_subscriber_flag ? "Pandora One": "standard Pandora") +
                  " subscriber.");
        m_pandora_remote.runPartnerLogin(pandora_one_subscriber_flag);
    }
    
    public void runUserLogin(String user, String password) throws HttpResponseException, 
                                                                  RPCException,
                                                                  IOException, 
                                                                  Exception{
        boolean needs_partner_login = false;
        boolean is_pandora_one_user = m_pandora_remote.isPandoraOneCredentials();
        boolean failure_but_not_epic_failure = true;
        while (failure_but_not_epic_failure){
            try{
                if (needs_partner_login){
                    m_prefs.edit().putBoolean("pandora_one_flag", is_pandora_one_user).apply();
                    runPartnerLogin(is_pandora_one_user);
                    needs_partner_login = false;
                }
                
                if (is_pandora_one_user){
                    m_audio_quality = PandoraRadio.MP3_192;
                }
                else {
                    m_audio_quality = PandoraRadio.MP3_128;
                }
                Log.i("Pandoroid", "Running a user login.");
                m_pandora_remote.connect(user, password);
                failure_but_not_epic_failure = false; //Or any type of fail for that matter.
            }
            catch (SubscriberTypeException e){
                needs_partner_login = true;
                is_pandora_one_user = e.is_pandora_one;
                Log.i("Pandoroid", 
                      "Wrong subscriber type. User is a " +
                      (is_pandora_one_user? "Pandora One": "standard Pandora") +
                      " subscriber.");
            }
            catch (RPCException e){
                if (e.code == RPCException.INVALID_AUTH_TOKEN){
                    needs_partner_login = true;
                    Log.e("Pandoroid", e.getMessage());
                }
                else{
                    throw e;
                }
            }
        }
    }
    
    public void setListener(Class<?> klass, Object listener) {
        listeners.put(klass, listener);
    }   
    
    public void setNotification() {
        if (!m_paused) {
            try {
                Song tmp_song;
                tmp_song = m_song_playback.getSong();
                Log.i("Pandoroid", "setNotification:" + tmp_song.getTitle() + " By " + tmp_song.getArtist());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationManager notificationManager =
                            (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
                    NotificationChannel notificationChannel = new NotificationChannel("default", "Pandoroid", NotificationManager.IMPORTANCE_LOW);
                    notificationChannel.setDescription("Channel description");
                    notificationChannel.enableLights(false);
                    notificationChannel.enableVibration(false);
                    notificationChannel.setLockscreenVisibility( Notification.VISIBILITY_PUBLIC);
                    notificationManager.createNotificationChannel(notificationChannel);
                }
                NotificationCompat.Builder mBuilder =
                        (NotificationCompat.Builder) new NotificationCompat.Builder(this, "default");
                Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.id.player_image);
                //private PandoraRadioService m_service;
                //m_service = ((PandoraRadioService.PandoraRadioBinder)m_service).getService();
                mBuilder
                        //.setLargeIcon(m_service.image_downloader.download(song.getAlbumCoverUrl(), image))
                        .setLargeIcon(largeIcon)
                        .setSmallIcon(R.drawable.notification_icon)
                        .setOngoing(true)
                        .setShowWhen(false)
                        .setStyle(new MediaStyle()
                                .setMediaSession(mSessionToken)
                                .setShowCancelButton(true)
                                .setCancelButtonIntent(
                                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                                                this, PlaybackStateCompat.ACTION_STOP
                                        )
                                )
                        )
                        .setContentText(tmp_song.getArtist())
                        .setContentInfo(tmp_song.getAlbum())
                        .setContentTitle(tmp_song.getTitle())
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

                Intent resultIntent = new Intent(this, PandoroidPlayer.class);
                PendingIntent resultPendingIntent =
                        PendingIntent.getActivity(
                                this,
                                0,
                                resultIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT
                        );
                mBuilder.setContentIntent(resultPendingIntent);
                int mNotificationId = 001;
                NotificationManager mNotifyMgr =
                        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                mNotifyMgr.notify(mNotificationId, mBuilder.build());
            } catch (Exception e) {
            }
        }
    }
    
    public void signOut() {
        if(m_song_playback != null) {
            stopForeground(true);
            m_song_playback.stop();
        }

        if(m_pandora_remote != null) {
            m_pandora_remote.disconnect();
        }
        
        if (m_current_station != null){
            m_current_station = null;
        }
    }
    
    public boolean isAlive() {
        return m_pandora_remote.isAlive();
    }
    
    public void updateStations() throws HttpResponseException, 
                                        RPCException, 
                                        IOException, 
                                        Exception {
        m_stations = m_pandora_remote.getStations();
    }
    
    public boolean setCurrentStation(String station_id) {
        for(int i = 0; i < m_stations.size(); ++i){
            Station tmp_station = m_stations.get(i);
            if (tmp_station.compareTo(station_id) == 0){
                m_current_station = tmp_station;
                stopForeground(true);
                setPlaybackController();
                m_prefs.edit().putString("lastStationId", station_id).apply();
                return true;
            }
        }
        
        return false;
    }
    

    
    public void playPause(){
        if (m_song_playback != null){
            if (!m_paused){
                pause();
            }
            else{
                play();
            }
        }
    }

    private void play() {
        m_paused = false;
        m_song_playback.play();
        setNotification();
        if (mWakeLock.isHeld()) {
            Log.i("Pandoroid", "WakeLock Already Active");
        } else {
            mWakeLock.acquire();
            Log.i("Pandoroid", "WakeLock Acquired");
        }
    }
    
    private void pause() {
        m_song_playback.pause();            
        m_paused = true;
        stopForeground(true);
        if (mWakeLock.isHeld()) {
            Log.i("Pandoroid", "WakeLock Active...releasing");
            mWakeLock.release();
        }
    }
    
    

    
    public void rate(String rating) {
        if(rating == PandoroidPlayer.RATING_NONE) {
            // cannot set rating to none
            return;
        }
        
        (new RateTask()).execute(rating);
    }
    
    public void resetPlaybackListeners(){
        if (m_song_playback != null){
            try {
                m_song_playback.setOnNewSongListener(
                        (OnNewSongListener) listeners.get(OnNewSongListener.class)
                                                  );
                m_song_playback.setOnPlaybackContinuedListener(
                        (OnPlaybackContinuedListener) listeners.get(OnPlaybackContinuedListener.class)
                                                               );
                m_song_playback.setOnPlaybackHaltedListener(
                        (OnPlaybackHaltedListener) listeners.get(OnPlaybackHaltedListener.class)
                                                           );
                m_song_playback.setOnErrorListener(new PlaybackOnErrorListener());

            } 
            catch (Exception e) {
                Log.e("Pandoroid", e.getMessage(), e);
            }
        }
    }
    
    private void setPlaybackController(){
        try{    
            if (m_song_playback == null){       
                m_song_playback = new MediaPlaybackController(m_current_station.getStationIdToken(),
                                                            PandoraRadio.AAC_32,
                                                            m_audio_quality,
                                                            m_pandora_remote,
                                                            connectivity_manager);

                
            }
            else{
                m_song_playback.reset(m_current_station.getStationIdToken(), m_pandora_remote);
                
            }
            resetPlaybackListeners();
        } 
        catch (Exception e) {
            Log.e("Pandoroid", e.getMessage(), e);
            m_song_playback = null;
        }
    }
    
    public void skip(){
        m_song_playback.skip();
    }
    
    public void startPlayback(){        
        if (m_song_playback != null){
            Thread t = new Thread(m_song_playback);
            t.start();
        }       
    }
    
    public void stopPlayback(){
        if (m_song_playback != null){
            m_song_playback.stop();
        }
        stopForeground(true);
    }
    
    public abstract static class OnInvalidAuthListener{
        public abstract void onInvalidAuth();
    }
    
    public class PlaybackOnErrorListener extends com.pandoroid.playback.OnErrorListener{
        public void onError(String error_message, 
                            Throwable e, 
                            boolean remote_error_flag,
                            int rpc_error_code){
            if (remote_error_flag){
                if (rpc_error_code == RPCException.INVALID_AUTH_TOKEN){
                    m_pandora_remote.disconnect();
                    OnInvalidAuthListener 
                        listener = (OnInvalidAuthListener) listeners.get(OnInvalidAuthListener.class);
                    if (listener != null){
                        listener.onInvalidAuth();
                    }
                }
            }           
        }
    }
    
    public class MusicIntentReceiver extends android.content.BroadcastReceiver {
        public void onReceive(Context ctx, Intent intent){
            if (intent.getAction().equals(
                    android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)){
                if (m_song_playback != null){
                    pause();
                }
            }
        }
    }
    
    public class RateTask extends AsyncTask<String, Void, Void>{
        public void onPreExecute(){
            try {
                this.m_song = m_song_playback.getSong();
            } catch (Exception e) {
                Log.e("Pandoroid", "No song to rate.");
            }
        }
        public Void doInBackground(String... ratings){
            if (m_song != null){
                String rating = ratings[0];             
                boolean rating_bool = rating.equals(PandoroidPlayer.RATING_LOVE) ? true : false;
                try {
                    m_pandora_remote.rate(this.m_song, rating_bool);
                    Log.i("Pandoroid", "A " + 
                                       (rating_bool ? "thumbs up" : "thumbs down") +
                                       " rating for the song " +
                                       this.m_song.getTitle() +
                                       " was successfully sent.");
                //We'll have to do more later, but this works for now.
//              } catch (HttpResponseException e) {
//              } catch (RPCException e) {
//              } catch (IOException e) {
                } 
                catch (Exception e) {
                    Log.e("Pandoroid", "Exception while sending a song rating.", e);
                }
            }
            return null;
        }
        
        private Song m_song;
    }
    
    /**
     * Description: An abstract asynchronous task for doing a generic login. 
     * @param <Params> -Parameters specific for the doInBackground() execution.
     */
    public abstract static class ServerAsyncTask<Params> extends AsyncTask<Params, 
                                                                           Void, 
                                                                           Integer> {
        protected static final int ERROR_UNSUPPORTED_API = 0;
        protected static final int ERROR_NETWORK = 1;
        protected static final int ERROR_UNKNOWN = 2;
        protected static final int ERROR_REMOTE_SERVER = 3;


        /**
         * Description: The required AsyncTask.doInBackground() function.
         */
        protected abstract Integer doInBackground(Params... args);
        
        protected abstract void quit();
        
        protected abstract void reportAction();
        
        /**
         * Description: A function that specifies the action to be taken
         *  when a user clicks the retry button in an alert dialog.
         */
        protected abstract void retryAction();
        
        protected abstract void showAlert(AlertDialog new_alert);

        /**
         * Description: Builds an alert dialog necessary for all login tasks.
         * @param error -The error code.
         * @return An alert dialog builder that can be converted into an alert
         *  dialog.
         */
        protected AlertDialog.Builder buildErrorDialog(int error, final Context context) {
            AlertDialog.Builder alert_builder = new AlertDialog.Builder(context);
            alert_builder.setCancelable(false);
            alert_builder.setPositiveButton("Quit",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            quit();
                        }
                    });

            switch (error) {
            case ERROR_NETWORK:
            case ERROR_UNKNOWN:
            case ERROR_REMOTE_SERVER:
                alert_builder.setNeutralButton("Retry",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                retryAction();
                            }
                        });
            }

            switch (error) {
            case ERROR_UNSUPPORTED_API:
                alert_builder.setNeutralButton("Report",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                reportAction();
                            }
                        });
                alert_builder.setMessage("Please update the app. "
                        + "The current Pandora API is unsupported.");
                break;
            case ERROR_NETWORK:
                alert_builder.setMessage("A network error has occurred.");
                break;
            case ERROR_UNKNOWN:
                alert_builder.setMessage("An unknown error has occurred.");
                break;
            case ERROR_REMOTE_SERVER:
                alert_builder
                        .setMessage("Pandora's servers are having troubles. "
                                + "Try again later.");
                break;
            }

            return alert_builder;
        }

        /**
         * Description: A test to show off different exceptions.
         * @throws RPCException
         * @throws HttpResponseException
         * @throws IOException
         * @throws Exception
         */
        public void exceptionTest() throws RPCException, HttpResponseException,
                IOException, Exception {
            switch (1) {
                case 0:
                    throw new RPCException(
                            RPCException.API_VERSION_NOT_SUPPORTED,
                            "Invalid API test");
                case 1:
                    throw new HttpResponseException(
                            HttpStatus.SC_INTERNAL_SERVER_ERROR,
                            "Internal server error test");
                case 2:
                    throw new IOException("IO exception test");
                case 3:
                    throw new Exception("Generic exception test");
            }
        }

        /**
         * Description: A handler that must be called when an RPCException 
         *  has occurred.
         * @param e
         * @return
         */
        protected int rpcExceptionHandler(RPCException e) {
            int success_flag = ERROR_UNKNOWN;
            if (RPCException.URL_PARAM_MISSING_METHOD <= e.code
                    && e.code <= RPCException.API_VERSION_NOT_SUPPORTED) {
                success_flag = ERROR_UNSUPPORTED_API;
            } else if (e.code == RPCException.INTERNAL
                    || e.code == RPCException.MAINTENANCE_MODE) {
                success_flag = ERROR_REMOTE_SERVER;
            } else {
                success_flag = ERROR_UNKNOWN;
            }

            return success_flag;
        }

        /**
         * Description: A handler that must be called when an HttpResponseException
         *  has occurred.
         * @param e
         * @return
         */
        protected int httpResponseExceptionHandler(HttpResponseException e) {
            int success_flag = ERROR_UNKNOWN;
            if (e.getStatusCode() == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
                success_flag = ERROR_REMOTE_SERVER;
            } else {
                success_flag = ERROR_NETWORK;
            }

            return success_flag;
        }

        /**
         * Description: A handler that must be called when an IOException
         *  has been encountered.
         * @param e
         * @return
         */
        protected int ioExceptionHandler(IOException e) {
            return ERROR_NETWORK;
        }

        /**
         * Description: A handler that must be called when a generic Exception has
         *  been encountered.
         * @param e
         * @return
         */
        protected int generalExceptionHandler(Exception e) {
            return ERROR_UNKNOWN;
        }
    }
}
