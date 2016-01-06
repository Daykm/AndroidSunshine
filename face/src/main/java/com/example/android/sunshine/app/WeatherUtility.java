package com.example.android.sunshine.app;

import android.content.Context;

public class WeatherUtility {

    public static String getGylphForWeather(int weatherId) {
        // TODO finish
        // Based on weather code data found at:
        // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
        // https://erikflowers.github.io/weather-icons/
        if (weatherId >= 200 && weatherId <= 232) {
            return "\uf07d";
        } else if (weatherId >= 300 && weatherId <= 321) {
            return "\uf009";
        } else if (weatherId >= 500 && weatherId <= 504) {
            return "\uf008";
        } else if (weatherId == 511) {
            return "\uf006";
        } else if (weatherId >= 520 && weatherId <= 531) {
            return "\uf019";
        } else if (weatherId >= 600 && weatherId <= 622) {
            return "\uf00b";
        } else if (weatherId >= 701 && weatherId <= 761) {
            return "\uf014";
        } else if (weatherId == 761 || weatherId == 781) {
            return "\uf01d";
        } else if (weatherId == 800) {
            return "\uf00d";
        } else if (weatherId == 801) {
            return "\uf041";
        } else if (weatherId >= 802 && weatherId <= 804) {
            return "\uf013";
        }
        return null;
    }

}
