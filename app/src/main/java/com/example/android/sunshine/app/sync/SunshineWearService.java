package com.example.android.sunshine.app.sync;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallbacks;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

public class SunshineWearService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    static final String TAG = SunshineWearService.class.getSimpleName();

    public static final String WEATHER_CAPABILITY_NAME="weather";
    public static final String WEATHER_PATH = "/weather";

    GoogleApiClient client;

    public String nodeId;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Create Service");
        if(client == null) {
            client = new GoogleApiClient.Builder(getApplicationContext())
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            client.connect();
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if(messageEvent.getPath().equals(WEATHER_PATH)) {
            notifyWear();
        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        Wearable.CapabilityApi.addCapabilityListener(client, this, WEATHER_CAPABILITY_NAME);
    }

    @Override
    public void onConnectionSuspended(int i) {
        switch(i) {
            case CAUSE_NETWORK_LOST:
                Log.i(TAG, "Google client suspended: Network lost");
                break;
            case CAUSE_SERVICE_DISCONNECTED:
                Log.i(TAG, "Google client suspended: Service has been killed");
                break;
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e(TAG, "Client connection error: " + connectionResult.getErrorMessage());
    }


    @Override
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        Log.i(TAG, "Capability changed");
        for(Node node : capabilityInfo.getNodes()) {
            Log.i(TAG, "Node ID: " + node.getId());
            nodeId = node.getId();
        }
    }

    private static final int FORECAST_LOADER = 0;
    // For the forecast view we're showing only a small subset of the stored data.
    // Specify the columns we need.
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

    // These indices are tied to FORECAST_COLUMNS.  If FORECAST_COLUMNS changes, these
    // must change.
    static final int COL_WEATHER_ID = 0;
    static final int COL_WEATHER_DATE = 1;
    static final int COL_WEATHER_DESC = 2;
    static final int COL_WEATHER_MAX_TEMP = 3;
    static final int COL_WEATHER_MIN_TEMP = 4;
    static final int COL_LOCATION_SETTING = 5;
    static final int COL_WEATHER_CONDITION_ID = 6;
    static final int COL_COORD_LAT = 7;
    static final int COL_COORD_LONG = 8;


    private void notifyWear() {
        Log.i(TAG, "Notify Wear");
        // This is called when a new Loader needs to be created.  This
        // fragment only uses one loader, so we don't care about checking the id.

        // To only show current and future dates, filter the query to return weather only for
        // dates after or including today.

        // Sort order:  Ascending, by date.
        String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";

        String locationSetting = Utility.getPreferredLocation(getApplicationContext());
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                locationSetting, System.currentTimeMillis());


        // TODO figure out why this is needed
        //Looper.prepare();

        CursorLoader loader = new CursorLoader(getApplicationContext(),
                weatherForLocationUri,
                FORECAST_COLUMNS,
                null,
                null,
                sortOrder);

        loader.registerListener(FORECAST_LOADER, new Loader.OnLoadCompleteListener<Cursor>() {
            @Override
            public void onLoadComplete(Loader<Cursor> loader, Cursor data) {
                data.moveToFirst();
                int low = data.getInt(COL_WEATHER_MIN_TEMP);
                int high = data.getInt(COL_WEATHER_MAX_TEMP);
                int weatherId = data.getInt(COL_WEATHER_CONDITION_ID);
                // String weather = Utility.getArtUrlForWeatherCondition(getApplicationContext(), weatherId);
                data.close();
                Log.i(TAG, "Temp low: "
                        + Integer.toString(low)
                        + ", high: "
                        + Integer.toString(high)
                        + ", "
                        //+ weather
                        //+ " "
                        + Integer.toString(weatherId));
                PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/weather");
                putDataMapRequest.getDataMap().putInt("temp-low", low);
                putDataMapRequest.getDataMap().putInt("temp-high", high);
                putDataMapRequest.getDataMap().putInt("weather", weatherId);
                //putDataMapRequest.getDataMap().putString("weather", weather);
                Wearable.DataApi.putDataItem(client, putDataMapRequest.asPutDataRequest()).setResultCallback(new ResultCallbacks<DataApi.DataItemResult>() {
                    @Override
                    public void onSuccess(@NonNull DataApi.DataItemResult dataItemResult) {
                        if (dataItemResult.getStatus().isSuccess()) {
                            Log.d(TAG, "Sent weather to wear");
                        } else {
                            Log.e(TAG, "Failed to send weather to wear");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Status status) {
                        Log.e(TAG, status.getStatusMessage());
                    }
                });
            }
        });

        loader.startLoading();
    }
}
