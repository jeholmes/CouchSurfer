/*
 * Copyright (c) 2012, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.jeholmes.couchsurf;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.ui.sfnative.SalesforceActivity;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;

public class MainActivity extends SalesforceActivity {

    private RestClient client;

    // UI variables
    private EditText addressField;
    Dialog loadingDialog;

    // User variables
	double userLat;
	double userLng;
    String userAddress = "";

    // List of returned properties
    private ArrayList<Property> nearestProperties;

    // Location variables
    private LocationManager locationManager;
    boolean locationEnabled;

    /**
     * Override back button to kill activity
     */
    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK))
        {
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Setup view
		setContentView(R.layout.main);
	}

    /**
     * onResume method initializes activity variables
     */
    @Override
	public void onResume() {
		// Hide everything until we are logged in
		findViewById(R.id.root).setVisibility(View.INVISIBLE);

		super.onResume();
	}

	@Override
    @SuppressLint("InflateParams") // To pass null to layout inflater
	public void onResume(RestClient client) {
        // Keeping reference to rest client
        this.client = client;

		// Show everything
		findViewById(R.id.root).setVisibility(View.VISIBLE);

        // If intent includes address string, update address field
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            addressField = (EditText) findViewById(R.id.address_field);
            userAddress = extras.getString("address");
            addressField.setText(userAddress);
        } else {
            addressField = (EditText) findViewById(R.id.address_field);
        }

        // Build loading dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        LayoutInflater inflater = MainActivity.this.getLayoutInflater();
        builder.setView(inflater.inflate(R.layout.loaddialog, null));
        loadingDialog = builder.create();
        loadingDialog.setCanceledOnTouchOutside(true);

        // Initialize list of returned properties
        nearestProperties = new ArrayList<>();
	}

    /**
     * Logout button click handler, logs out salesforce user
     */
	public void onLogoutClick(View v) {
		SalesforceSDKManager.getInstance().logout(this);
	}

    /**
     * GPS search button click handler, checks location service before getting GPS coordinates
     */
    public void onMyLocationClick (View v) throws UnsupportedEncodingException {
        checkLocationService();

        if (locationEnabled) {
            new myLocationTask().execute();
        }
    }

    /**
     * Address search button click handler, geocodes the address in the address field to coordinates
     */
    public void onSearchClick (View v) {
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

        new geocodeTask().execute();
    }

    /**
     * Register button click handler, sends intent to RegisterActivity
     */
    public void onRegisterClick (View v) {
        Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
        startActivity(intent);
        finish();
    }

    /**
     * Starts MapActivity with property information from list bundled as extras
     */
    private void sendTransitionIntent() {
        Intent intent = new Intent(MainActivity.this, MapsActivity.class);
        intent.putExtra("total", nearestProperties.size());
        intent.putExtra("lat", userLat);
        intent.putExtra("lng", userLng);
        intent.putExtra("address", userAddress);
        for (int i = 0; i < nearestProperties.size(); i++) {
            intent.putExtra(i + "-id", nearestProperties.get(i).id);
            intent.putExtra(i + "-name", nearestProperties.get(i).name);
            intent.putExtra(i + "-lat", nearestProperties.get(i).lat);
            intent.putExtra(i + "-lng", nearestProperties.get(i).lng);
            intent.putExtra(i + "-avail", nearestProperties.get(i).available);
            intent.putExtra(i + "-total", nearestProperties.get(i).total);
        }
        startActivity(intent);
        finish();
    }

    /**
     * Geocodes the user address using Google Maps' geocode API
     */
    private void geocodeAddress () {
        Log.v("geocode", "defining http url");
        HttpGet httpGet = null;
        try {
            // Build http url for get request
            httpGet = new HttpGet(
                    "http://maps.google.com/maps/api/geocode/json?address=" +
                            URLEncoder.encode(userAddress + ", BC, Canada", "UTF-8") +
                            "&ka&sensor=false");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        // Create http client and response structures
        HttpClient httpclient = new DefaultHttpClient();
        HttpResponse response;
        StringBuilder stringBuilder = new StringBuilder();


        Log.v("geocode", "executing http get");
        try {
            // Send http get request and build response string
            response = httpclient.execute(httpGet);
            HttpEntity entity = response.getEntity();
            InputStream stream = entity.getContent();
            int b;
            while ((b = stream.read()) != -1) {
                stringBuilder.append((char) b);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.v("geocode", "building json object");
        try {
            // Convert response string to JSON object
            JSONObject jsonObject = new JSONObject(stringBuilder.toString());

            // Extract longitude element
            userLng = ((JSONArray) jsonObject.get("results")).getJSONObject(0)
                    .getJSONObject("geometry").getJSONObject("location")
                    .getDouble("lng");

            // Extract latitude element
            userLat = ((JSONArray) jsonObject.get("results")).getJSONObject(0)
                    .getJSONObject("geometry").getJSONObject("location")
                    .getDouble("lat");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.v("geocode", "done");
    }

    /**
     * Waits to check if user coordinates are defined, then sends query and waits for response
     */
    private void waitAndSendQuery() {
        // Wait for user coordinates to be defined
        int i = 0;
        while ( userLat == 0.0 || userLng == 0.0 && i < getResources().getInteger(R.integer.timeout)) {
            try {
                // Wait a second
                Thread.sleep(1000);
                Log.v("busy wait", "waiting one second");
                i++;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (userLat != 0.0 && userLng != 0.0) {
            // Send query for properties based on user coordinates
            try {
                sendRequest("SELECT Name, Id, Location__Latitude__s, Location__Longitude__s, " +
                        "Available_Couches__c, Total_Couches__c\n" +
                        "FROM Property__c\n" +
                        "ORDER BY DISTANCE(Location__c, " +
                        "GEOLOCATION(" + userLat + "," + userLng + "), 'km') ASC\n" +
                        "LIMIT 10");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

        // Wait for response to populate property list
        i = 0;
        while ( nearestProperties.size() == 0 && i < getResources().getInteger(R.integer.timeout)) {
            try {
                // Wait a second
                Thread.sleep(1000);
                Log.v("busy wait", "waiting one second");
                i++;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        }
    }

    /**
     * Sends soql query request
     * @param soql soql query string
     */
	private void sendRequest(String soql) throws UnsupportedEncodingException {
		RestRequest restRequest = RestRequest.getRequestForQuery(getString(R.string.api_version), soql);

        // Send Async query request
        Log.v("connection", "sending async request");
		client.sendAsync(restRequest, new RestClient.AsyncRequestCallback() {
            // If request is successful then convert response records to property objects
            @Override
            public void onSuccess(RestRequest request, RestResponse result) {
                try {
                    nearestProperties.clear();
                    JSONArray records = result.asJSONObject().getJSONArray("records");
                    for (int i = 0; i < records.length(); i++) {
                        JSONObject record = records.getJSONObject(i);
                        Property properties = new Property(record.getString("Name"), record.getString("Id"), record.getDouble("Location__Latitude__s"), record.getDouble("Location__Longitude__s"), record.getDouble("Available_Couches__c"), record.getDouble("Total_Couches__c"));
                        nearestProperties.add(properties);
                    }
                    Log.v("connection", "success");
                } catch (Exception e) {
                    Log.v("connection", "failure");
                    onError(e);
                }
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(MainActivity.this,
                        MainActivity.this.getString(SalesforceSDKManager.getInstance().getSalesforceR().stringGenericError(), exception.toString()),
                        Toast.LENGTH_LONG).show();
            }
        });
	}

    /**
     * Displays alert dialog if no address is entered when trying to search by address
     */
    private void addressFailed () {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("No address entered");
        builder.setMessage("Please enter a valid address");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
            }
        });
        Dialog alertDialog = builder.create();
        alertDialog.setCanceledOnTouchOutside(true);
        alertDialog.show();
    }

    /**
     * Displays alert dialog if query was unable to receive records
     */
    private void queryFailed () {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Connection Failed");
        builder.setMessage("Could not query Salesforce, please refresh login");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                SalesforceSDKManager.getInstance().logout(MainActivity.this);
            }
        });
        Dialog alertDialog = builder.create();
        alertDialog.setCanceledOnTouchOutside(true);
        alertDialog.show();
    }

    /**
     * Checks location service, once enabled it gets the GPS coordinates then disables
     */
    private void checkLocationService() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            // Display alert dialog if location service is disabled
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("Location Services Not Active");
            builder.setMessage("Please enable Location Services and GPS");
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialogInterface, int i) {
                    // Show location settings when the user acknowledges the alert dialog
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                    locationEnabled = true;
                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialogInterface, int i) {
                    locationEnabled = false;
                }
            });
            Dialog alertDialog = builder.create();
            alertDialog.setCanceledOnTouchOutside(true);
            alertDialog.show();
        } else {
            locationEnabled = true;
        }

        // Define location listener to disable once coordinates received
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider
                userLat = location.getLatitude();
                userLng = location.getLongitude();

                // Removes the listener
                locationManager.removeUpdates(this);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}
            public void onProviderEnabled(String provider) {}
            public void onProviderDisabled(String provider) {}
        };

        // Request location updates from GPS or network
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
    }

    /**
     * AsyncTask to handle querying for properties by GPS coordinates
     */
    private class myLocationTask extends AsyncTask<Void,Void,Boolean > {

        protected void onPreExecute() {
            loadingDialog.show();
        }

        protected Boolean doInBackground(Void... params) {
            if (locationEnabled) {
                waitAndSendQuery();
            }
            return (nearestProperties.size() != 0);
        }

        protected void onPostExecute(Boolean result) {
            if (result) {
                sendTransitionIntent();
            } else {
                loadingDialog.dismiss();
                queryFailed();
            }
        }
    }

    /**
     * AsyncTask to handle querying for properties by geocoding address
     */
    private class geocodeTask extends AsyncTask<Void,Void,Integer> {

        protected void onPreExecute() {
            userAddress = addressField.getText().toString();
            loadingDialog.show();
        }

        protected Integer doInBackground(Void... params) {
            if (userAddress.length() == 0) {
                return 2;
            } else {
                geocodeAddress();
                waitAndSendQuery();
                if (nearestProperties.size() != 0) {
                    return 0;
                }
                else {
                    return 1;
                }
            }
        }

        protected void onPostExecute(Integer result) {
            if (result == 0) {
                sendTransitionIntent();
            } else if (result == 1) {
                loadingDialog.dismiss();
                queryFailed();
            } else if (result == 2) {
                loadingDialog.dismiss();
                addressFailed();
            }
        }
    }

    /**
     * Class definition for property object
     */
    class Property {
        public String name;
        public String id;
        public double lat;
        public double lng;
        public double available;
        public double total;

        public Property(String name, String id, double lat, double lng, double available, double total) {
            this.name = name;
            this.id = id;
            this.lat = lat;
            this.lng = lng;
            this.available = available;
            this.total = total;
        }

        public String toString() {
            return name;
        }
    }
}
