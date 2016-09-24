package com.example.android.sunshine.app;

import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import java.nio.ByteBuffer;

/**
 * Created by Greg on 23-09-2016.
 */
public class ListenerService extends WearableListenerService {
    private static final String weatherMessagePath = "/sunshine-weather";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.i("sunshine wearable", "received message");

        if(messageEvent.getPath().equals(weatherMessagePath)){

            byte[] combined=   messageEvent.getData();
            if(combined.length == 12) {
                MyWatchFace.CurrentHigh = fromByteArray(new byte[]{combined[0], combined[1], combined[2], combined[3]});
                MyWatchFace.CurrentLow = fromByteArray(new byte[]{combined[4], combined[5], combined[6], combined[7]});
                int weatherConditions = fromByteArray(new byte[]{combined[8], combined[9], combined[10], combined[11]});
                Log.i("sunshine wearable", String.format("weatherConditions %d", weatherConditions));
                MyWatchFace.CurrentImage = getIconResourceForWeatherCondition(fromByteArray(new byte[]{combined[8], combined[9], combined[10], combined[11]}));
                Log.i("sunshine wearable", "weather message");
            }
        }
    }

    private int fromByteArray(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }
    private  int getIconResourceForWeatherCondition(int weatherId) {
        // Based on weather code data found at:
        // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
        if (weatherId >= 200 && weatherId <= 232) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            return R.drawable.ic_light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            return R.drawable.ic_rain;
        } else if (weatherId == 511) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 520 && weatherId <= 531) {
            return R.drawable.ic_rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 701 && weatherId <= 761) {
            return R.drawable.ic_fog;
        } else if (weatherId == 761 || weatherId == 781) {
            return R.drawable.ic_storm;
        } else if (weatherId == 800) {
            return R.drawable.ic_clear;
        } else if (weatherId == 801) {
            return R.drawable.ic_light_clouds;
        } else if (weatherId >= 802 && weatherId <= 804) {
            return R.drawable.ic_cloudy;
        }
        return -1;
    }
}
