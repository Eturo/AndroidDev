package com.example.eturo.sunshineapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {

    private ArrayAdapter<String> weather;

    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.forecastfragment,menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_refresh) {
            updateWeather();

            return true;
        }


        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart(){
        super.onStart();
        updateWeather();
    }

    public void updateWeather(){
        FetchWeatherTask fetch = new FetchWeatherTask();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        //the second parameter is the value that location will be if the string found doesn't exist, aka the default value.
        String location = sp.getString(getString(R.string.pref_location_key),getString(R.string.pref_location_default));
        Log.d("Update weather", location);
        fetch.execute(location);
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View rootView=inflater.inflate(R.layout.fragment_main, container, false);
        weather = new ArrayAdapter<String>(getActivity(),R.layout.list_item_forecast,R.id.list_item_forecast_textview,new ArrayList<String>());

        ListView mainListView = (ListView)rootView.findViewById(R.id.listview_forecast);
        mainListView.setAdapter(weather);
        mainListView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.v("onItemClick", " Begin click listener:");
                String forecast = parent.getItemAtPosition(position).toString();
                Intent displayDetails = new Intent(getActivity(),DetailActivity.class)
                        .putExtra(Intent.EXTRA_TEXT, forecast);
                startActivity(displayDetails);

            }
        });

        return rootView;
    }

    public class FetchWeatherTask extends AsyncTask<String,Void,String[]> {

        @Override
        protected void onPostExecute(String[] strings) {
            super.onPostExecute(strings);
            Log.v("onPostExecute", "Executing after doInBackground");
            weather.clear();
            for(String dayForecastStr : strings)
                weather.add(dayForecastStr);


        }

        /* The date/time conversion code is going to be moved outside the asynctask later,
                 * so for convenience we're breaking it out into its own method now.
                 */
        private String getReadableDateString(long time){
            // Because the API returns a unix timestamp (measured in seconds),
            // it must be converted to milliseconds in order to be converted to valid date.
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
            return shortenedDateFormat.format(time);
        }

        /**
         * Prepare the weather high/lows for presentation.
         */
        private String formatHighLows(double high, double low,String unitType) {
            // For presentation, assume the user doesn't care about tenths of a degree.



            if(unitType.equals(getString(R.string.pref_unit_value_fahrenheit))){
                high = (high * 1.8) + 32;
                low = (low * 1.8) + 32;
            }else if(!unitType.equals(getString(R.string.pref_unit_value_celcius))){
                Log.d("ForecastFragment", "Unit type not found: " + unitType);

            }


            //the second parameter is the value that location will be if the string found doesn't exist, aka the default value.
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);
            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }

        /**
         * Take the String representing the complete forecast in JSON Format and
         * pull out the data we need to construct the Strings needed for the wireframes.
         *
         * Fortunately parsing is easy:  constructor takes the JSON string and converts it
         * into an Object hierarchy for us.
         */
        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws JSONException {

            // These are the names of the JSON objects that need to be extracted.
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());

            String unitType = sp.getString(getString(R.string.pref_unit_key),getString(R.string.pref_unit_value_celcius));
            Log.d("ForecastFragment",unitType);
            // OWM returns daily forecasts based upon the local time of the city that is being
            // asked for, which means that we need to know the GMT offset to translate this data
            // properly.

            // Since this data is also sent in-order and the first day is always the
            // current day, we're going to take advantage of that to get a nice
            // normalized UTC date for all of our weather.

            Time dayTime = new Time();
            dayTime.setToNow();

            // we start at the day returned by local time. Otherwise this is a mess.
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            // now we work exclusively in UTC
            dayTime = new Time();

            String[] resultStrs = new String[numDays];
            for(int i = 0; i < weatherArray.length(); i++) {
                // For now, using the format "Day, description, hi/low"
                String day;
                String description;
                String highAndLow;

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                // The date/time is returned as a long.  We need to convert that
                // into something human-readable, since most people won't read "1400356800" as
                // "this saturday".
                long dateTime;
                // Cheating to convert this to UTC time, which is what we want anyhow
                dateTime = dayTime.setJulianDay(julianStartDay+i);
                day = getReadableDateString(dateTime);

                // description is in a child array called "weather", which is 1 element long.
                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                // Temperatures are in a child object called "temp".  Try not to name variables
                // "temp" when working with temperature.  It confuses everybody.
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);

                highAndLow = formatHighLows(high, low, unitType);
                resultStrs[i] = day + " - " + description + " - " + highAndLow;
            }

            for (String s : resultStrs) {
                Log.v("FetchWeatherForecast", "Forecast entry: " + s);
            }
            return resultStrs;

        }

        @Override
        protected String[] doInBackground(String... params) {

            //These two need to be declared outside the try/catch
            //so that they can be closed in finally block
            String format = "json";
            String unit = "metric";
          //  ArrayList<String> data = new ArrayList<String>();
            int numDays = 7;
            final String APPID="bbcb2400757af7b40198b4c635224375";
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            //Will contain the JSON response as string
            String forecastJsonStr = null;

            try {
                //Construct the url for the query
                final String FORECASE_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String QUERY_PARAM= "q";
                final String FORMAT_PARAM="mode";
                final String UNITS_PARAM="units";
                final String DAYS_PARAM = "cnt";
                final String APP_ID = "APPID";
                Log.d("Pref location update:", "Location:" + params[0]);
                Uri builtUri = Uri.parse(FORECASE_BASE_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAM,params[0])
                        .appendQueryParameter(FORMAT_PARAM,format)
                        .appendQueryParameter(UNITS_PARAM,unit)
                        .appendQueryParameter(DAYS_PARAM,Integer.toString(numDays))
                        .appendQueryParameter(APP_ID,APPID)
                        .build();
                URL url = new URL(builtUri.toString());

                //create request to OpenWeatherMap, open connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null)
                    return null;
                reader = new BufferedReader(new InputStreamReader((inputStream)));

                String line;
                while ((line = reader.readLine()) != null)
                    //since it's json, adding newline isn't necessary (it wont effect parsing)
                    //but it does make debugging a lot easier if you print out the completed buffer for debugging.
                    buffer.append(line + "\n");


                if (buffer.length() == 0)
                    return null;
                forecastJsonStr = buffer.toString();
                try {
                   String[] data= getWeatherDataFromJson(forecastJsonStr, numDays);
                    return data;
                }catch(JSONException jsone){
                    Log.e("getWeatherDataFromJson", "Error calling " + jsone);
                }

                Log.v("ForecastFragment","Forecast JSON String:" + forecastJsonStr);

            } catch (IOException e) {
                Log.e("PlaceHolderFragment", "Error", e);
                return null;

            } finally {
                if (urlConnection != null)
                    urlConnection.disconnect();
                if (reader != null) {
                    try {
                        reader.close();

                    } catch (final IOException e) {
                        Log.e("PlaceHolderFragment", "Error closing Stream", e);
                    }
                }
            }

            return null;
        }
    }
}
