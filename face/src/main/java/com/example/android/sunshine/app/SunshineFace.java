/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class SunshineFace extends CanvasWatchFaceService implements DataApi.DataListener, CapabilityApi.CapabilityListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, ResultCallback<CapabilityApi.GetCapabilityResult> {
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    public static final String TAG = SunshineFace.class.getSimpleName();

    public static final String WEATHER_CAPABILITY_NAME="weather";

    GoogleApiClient mClient;

    Engine mEngine;

    public static final String TEMPHIGH = "temp-high";
    public static final String TEMPLOW = "temp-low";
    public static final String WEATHER = "weather";

    int tempHigh = 99, tempLow = 99;
    String weather = "\uf00d";

    String nodeId;

    @Override
    public Engine onCreateEngine() {
        mEngine = new Engine();
        mClient = new GoogleApiClient.Builder(getApplicationContext())
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();


        return mEngine;
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {

        Log.i(TAG, "Data changed");
        for (DataEvent dataEvent : dataEventBuffer) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                String path = dataEvent.getDataItem().getUri().getPath();

                if (path.equals("/weather")) {
                    setWeather(dataEvent.getDataItem());

                }
            }
        }
    }

    public String getWeatherUTFCodeFromID(int id) {
        // TODO match ID to utf code
        return Integer.toString(id);
    }



    @Override
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        Log.i(TAG, "Capability Changed");
        for(Node node: capabilityInfo.getNodes()) {
            Log.i(TAG, "Node Id: " + node.getId() + ", Node is nearby: " + node.isNearby());
            nodeId = node.getId();
        }

        if(nodeId != null) {
           requestWeatherData();
        }
    }

    public void requestWeatherData() {

        Uri uri = new Uri.Builder()
                .scheme("wear")
                .path("/weather")
                .authority(nodeId)
                .build();

        Wearable.DataApi.getDataItem(mClient, uri).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(DataApi.DataItemResult dataItemResult) {
                setWeather(dataItemResult.getDataItem());
            }
        });
    }

    public void setWeather(DataItem item) {
        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
        tempLow = dataMap.getInt(TEMPHIGH);
        tempHigh = dataMap.getInt(TEMPLOW);
        weather = WeatherUtility.getGylphForWeather(dataMap.getInt(WEATHER));
        Log.i(TAG, "Temp low: " + Integer.toString(tempLow) + ", Temp high: " + Integer.toString(tempHigh) + ", Weather: " + weather);


    }

    @Override
    public void onConnected(Bundle bundle) {
        Wearable.DataApi.addListener(mClient, this);

        Wearable.CapabilityApi.addCapabilityListener(mClient, this, "weather");
        Wearable.CapabilityApi.getCapability(mClient, WEATHER_CAPABILITY_NAME , CapabilityApi.FILTER_REACHABLE).setResultCallback(this);
        Log.i(TAG, "Google api client connected");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Google api client disconnected");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e(TAG, connectionResult.toString());
    }

    private void requestWeatherUpdate() {
        if(nodeId != null) {
            Log.i(TAG, "Request Weather: " + nodeId);
            Wearable.MessageApi.sendMessage(mClient, nodeId, "/weather", new DataMap().toByteArray())
                    .setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                        @Override
                        public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                            Log.d(TAG, "Sent message status: " + sendMessageResult.getStatus());
                        }
                    });
        }
    }

    @Override
    public void onResult(CapabilityApi.GetCapabilityResult getCapabilityResult) {
        Log.i(TAG, getCapabilityResult.getStatus().getStatusMessage());
        Log.i(TAG, "Capabilities retrieved");
        for(Node node : getCapabilityResult.getCapability().getNodes()) {
            Log.i(TAG, "Node: " + node.getId());
            nodeId = node.getId();
        }

        if(nodeId != null) {
            requestWeatherData();
        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineFace.Engine> mWeakReference;

        public EngineHandler(SunshineFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mHandPaint;
        Paint mTimePaint;
        Paint mDatePaint;
        Paint mWeatherPaint;
        boolean mAmbient;
        GregorianCalendar mCal;
        SimpleDateFormat mDateFormat = new SimpleDateFormat("EE, MMM DD yyyy");
        SimpleDateFormat mTimeFormatNoS = new SimpleDateFormat("HH:mm");
        SimpleDateFormat mTimeFormatS = new SimpleDateFormat("HH:mm:ss");
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCal.setTimeZone(TimeZone.getTimeZone(intent.getStringExtra("time-zone")));
                //mTime.clear(intent.getStringExtra("time-zone"));
                //mTime.setToNow();
            }
        };
        int mTapCount;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .setStatusBarGravity(Gravity.TOP | Gravity.CENTER)
                    .setHotwordIndicatorGravity(Gravity.TOP | Gravity.CENTER)
                    .build());


            requestWeatherUpdate();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(getColor(R.color.bluePrimary));

            mHandPaint = new Paint();
            mHandPaint.setColor(getColor(R.color.analog_hands));
            mHandPaint.setStrokeWidth(getResources().getDimension(R.dimen.analog_hand_stroke));
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);

            Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/goudy_bookletter_1911.otf");
            mTimePaint = new Paint();
            mTimePaint.setColor(getColor(R.color.clock_text));
            mTimePaint.setTextSize(getResources().getDisplayMetrics().density * 45);
            mTimePaint.setTypeface(typeface);
            mTimePaint.setAntiAlias(true);
            mDatePaint = new Paint();
            mDatePaint.setColor(getColor(R.color.clock_text));
            mDatePaint.setTextSize(getResources().getDisplayMetrics().density * 20);
            mDatePaint.setTypeface(typeface);
            mDatePaint.setAntiAlias(true);

            Typeface weatherType = Typeface.createFromAsset(getAssets(), "fonts/weathericons-regular-webfont.ttf");
            mWeatherPaint = new Paint();
            mWeatherPaint.setColor(getColor(R.color.clock_text));
            mWeatherPaint.setAntiAlias(true);
            mWeatherPaint.setTypeface(weatherType);
            mWeatherPaint.setTextSize(getResources().getDisplayMetrics().density * 10);
            mCal = new GregorianCalendar();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHandPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mCal.setTimeInMillis(System.currentTimeMillis());
            requestWeatherUpdate();

            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
                drawTextCentered(canvas, mTimeFormatNoS.format(mCal.getTime()), mTimePaint, 0.4f);
            } else {
                canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);
                drawTextCentered(canvas, mTimeFormatS.format(mCal.getTime()), mTimePaint, 0.3f);
                drawTextCentered(canvas, mDateFormat.format(mCal.getTime()), mDatePaint, 0.65f);
            }

            drawTextCentered(canvas, weather, mWeatherPaint, 1.0f);

            drawTextCentered(canvas, Integer.toString(tempHigh) + "°  " + Integer.toString(tempLow) + "°", mDatePaint, 0.85f);

        }

        /**
         * The location of the top of of the text ranging from 0 to 1, as the distance from to
         * the top of the screen to the bottom
         *
         * @param canvas
         * @param time
         * @param paint
         * @param topTextStart
         */
        public void drawTextCentered(Canvas canvas, String time, Paint paint, float topTextStart) {
            // the Paint instance(should be assign as a field of class).

            // the display area.
            canvas.getClipBounds();
            Rect areaRect = canvas.getClipBounds();
            RectF bounds = new RectF(areaRect);

            bounds.right = paint.measureText(time, 0, time.length());
            bounds.bottom = paint.descent() - paint.ascent();

            bounds.left += (areaRect.width() - bounds.right) / 2.0f;
            bounds.top += ((areaRect.height() - bounds.bottom)) * topTextStart;

            canvas.drawText(time, bounds.left, bounds.top - paint.ascent(), paint);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                // Update time zone in case it changed while we weren't visible.
                mCal.setTimeZone(TimeZone.getDefault());
                mCal.setTimeInMillis(System.currentTimeMillis());
                //mTime.clear(TimeZone.getDefault().getID());
                //mTime.setToNow();

                mClient.connect();
            } else {
                unregisterReceiver();
                if(mClient != null && mClient.isConnected()) {
                    Wearable.DataApi.removeListener(mClient, SunshineFace.this);
                    mClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
