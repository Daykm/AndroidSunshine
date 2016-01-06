package com.example.android.sunshine.app.sync;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;

public class LoggingCallbacks implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static final String TAG = LoggingCallbacks.class.getSimpleName();
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "Google API client connected");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Google Api client suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e(TAG, connectionResult.getErrorMessage());
    }
}