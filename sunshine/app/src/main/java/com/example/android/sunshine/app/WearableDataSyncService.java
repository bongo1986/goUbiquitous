package com.example.android.sunshine.app;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;

import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Greg on 23-09-2016.
 */
public class WearableDataSyncService extends Service {


    private static final int COMMUNICATION_INTERVAL_MS = 1000;
    private static final int COMMUNICATION_TIMEOUT_MS = 1000;
    private static final int SENT_DATA_INTERVAL_SECONDS = 15;
    private Integer m_CurrentLow;
    private Integer m_CurrentHigh;
    private Integer m_CurrentImage;
    private Calendar m_DataSentTimestamp;

    private Integer m_SentLow;
    private Integer m_SentHigh;
    private Integer m_SentImage;

    private static final String[] FORECAST_COLUMNS = {
            // In this case the id needs to be fully qualified with a table name, since
            // the content provider joins the location & weather tables in the background
            // (both have an _id column)
            // On the one hand, that's annoying.  On the other, you can search the weather table
            // using the location set by the user, which is only in the Location table.
            // So the convenience is worth it.
            WeatherContract.WeatherEntry.TABLE_NAME + "." + WeatherContract.WeatherEntry._ID,
            WeatherContract.WeatherEntry.COLUMN_DATE,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.LocationEntry.COLUMN_COORD_LAT,
            WeatherContract.LocationEntry.COLUMN_COORD_LONG
    };

    private boolean m_ContinueSendingData;

    private GoogleApiClient getGoogleApiClient(Context context) {
        return new GoogleApiClient.Builder(context)
                .addApi(Wearable.API)
                .build();
    }

    @Override
    public void onCreate() {
        m_ContinueSendingData = true;
        run();
    }
    @Override
    public void onDestroy() {
        m_ContinueSendingData = false;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void updateCurrentWeatherData() {
        // This is called when a new Loader needs to be created.  This
        // fragment only uses one loader, so we don't care about checking the id.

        // To only show current and future dates, filter the query to return weather only for
        // dates after or including today.

        // Sort order:  Ascending, by date.
        String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";

        String locationSetting = Utility.getPreferredLocation(getBaseContext());
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                locationSetting, System.currentTimeMillis());



        Cursor c = getContentResolver().query(
                weatherForLocationUri,   // The content URI of the words table
                FORECAST_COLUMNS,                        // The columns to return for each row
                null,                    // Selection criteria
                null,                     // Selection criteria
                sortOrder);

        if(c != null && c.moveToFirst()){
            int weatherId = c.getInt(ForecastFragment.COL_WEATHER_CONDITION_ID);
            m_CurrentImage = weatherId;

            m_CurrentHigh = (int)c.getDouble(ForecastFragment.COL_WEATHER_MAX_TEMP);
            // Read low temperature from cursor
            m_CurrentLow = (int)c.getDouble(ForecastFragment.COL_WEATHER_MIN_TEMP);

            byte[] byteArrToSend =getBytesToSend();
        }


    }

    private void run(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (m_ContinueSendingData) {
                    updateCurrentWeatherData();
                    Calendar c = Calendar.getInstance();
                    c.add(Calendar.SECOND, -SENT_DATA_INTERVAL_SECONDS);

                    //data never sent or update needed
                    if(m_DataSentTimestamp == null && m_CurrentHigh != null || (m_DataSentTimestamp != null && c.compareTo(m_DataSentTimestamp) >0 && m_CurrentHigh != null)) {
                        sendWeatherDataToWearable();
                    }
                    try {
                        Thread.sleep(COMMUNICATION_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }}).start();
    }

    private void sendWeatherDataToWearable(){
        final Context context = getBaseContext();
        new Thread(new Runnable() {
            @Override
            public void run() {

                    final GoogleApiClient client = getGoogleApiClient(context);
                    Log.i("sunshinewearable", "before connecting");

                    String nodeId = "";
                    client.blockingConnect(COMMUNICATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    NodeApi.GetConnectedNodesResult result =
                            Wearable.NodeApi.getConnectedNodes(client).await();
                    List<Node> nodes = result.getNodes();

                    if (nodes.size() > 0) {
                        nodeId = nodes.get(0).getId();
                    }
                    client.disconnect();
                    Log.i("sunshinewearable", "before sending message to wearable");

                    if (nodeId != "") {
                        Log.i("sunshinewearable", "sending message to wearable");

                        client.blockingConnect(COMMUNICATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        byte[] byteArrToSend =getBytesToSend();

                        Wearable.MessageApi.sendMessage(client, nodeId, "/sunshine-weather", byteArrToSend);
                        client.disconnect();

                        m_SentHigh = m_CurrentHigh;
                        m_SentLow = m_CurrentLow;
                        m_SentImage = m_CurrentImage;
                        m_DataSentTimestamp = Calendar.getInstance();

                        Log.i("sunshinewearable", "message sent");
                    }

            }
        }).start();
    }

    private byte[] getBytesToSend(){
        byte[] high = intToByteArray(m_CurrentHigh);
        byte[] low = intToByteArray(m_CurrentLow);
        byte[] img = intToByteArray(m_CurrentImage);

        byte[] combined = new byte[high.length + low.length + img.length];

        combined[0] = high[0];
        combined[1] = high[1];
        combined[2] = high[2];
        combined[3] = high[3];

        combined[4] = low[0];
        combined[5] = low[1];
        combined[6] = low[2];
        combined[7] = low[3];

        combined[8] = img[0];
        combined[9] = img[1];
        combined[10] = img[2];
        combined[11] = img[3];


        return combined;

    }



    public byte[] intToByteArray(int value) {
        return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
    }
}
