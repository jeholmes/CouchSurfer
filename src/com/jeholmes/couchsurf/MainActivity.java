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
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
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

/**
 * Main activity
 */
public class MainActivity extends SalesforceActivity {

    private RestClient client;
    private ArrayList<Properties> nearestProperties;

    private LocationManager locationManager;

	double userLat;
	double userLng;
    String userAddress = "";

    private EditText addressField;

    boolean locationEnabled;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            userAddress = extras.getString("address");
        }

		// Setup view
		setContentView(R.layout.main);
	}
	
	@Override 
	public void onResume() {
		// Hide everything until we are logged in
		findViewById(R.id.root).setVisibility(View.INVISIBLE);

        addressField = (EditText) findViewById(R.id.address_field);

        nearestProperties = new ArrayList<>();

		super.onResume();
	}

	@Override
	public void onResume(RestClient client) {
        // Keeping reference to rest client
        this.client = client;

		// Show everything
		findViewById(R.id.root).setVisibility(View.VISIBLE);
	}

	public void onLogoutClick(View v) {
		SalesforceSDKManager.getInstance().logout(this);
	}

    @SuppressLint("InflateParams")
    public void onSearchClick (View v) {

        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

        userAddress = addressField.getText().toString();

        if (userAddress.length() == 0) {
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
        } else {

            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            LayoutInflater inflater = MainActivity.this.getLayoutInflater();
            builder.setView(inflater.inflate(R.layout.load_dialog, null));
            Dialog loadingDialog = builder.create();
            loadingDialog.setCanceledOnTouchOutside(true);
            loadingDialog.show();

            geocodeThread.start();
        }
    }

    @SuppressLint("InflateParams")
    public void onMyLocationClick (View v) throws UnsupportedEncodingException {

        // Get Location Manager and check for GPS & Network location services
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);



        if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            // Build the alert dialog
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

        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.
                userLat = location.getLatitude();
                userLng = location.getLongitude();

                // Remove the listener you
                locationManager.removeUpdates(this);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}

            public void onProviderEnabled(String provider) {}

            public void onProviderDisabled(String provider) {}
        };

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);

        if (locationEnabled) {

            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            LayoutInflater inflater = MainActivity.this.getLayoutInflater();
            builder.setView(inflater.inflate(R.layout.load_dialog, null));
            Dialog loadingDialog = builder.create();
            loadingDialog.setCanceledOnTouchOutside(true);
            loadingDialog.show();

            queryThread.start();
        }
    }

    Thread geocodeThread  = new Thread() {
        public void run() {

            Log.v("geocode","before http url define");
            HttpGet httpGet = null;
            try {
                httpGet = new HttpGet(
                        "http://maps.google.com/maps/api/geocode/json?address="
                                + URLEncoder.encode(userAddress + ", BC, Canada", "UTF-8") + "&ka&sensor=false");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            HttpClient httpclient = new DefaultHttpClient();
            HttpResponse response;
            StringBuilder stringBuilder = new StringBuilder();

            Log.v("geocode","before http get");
            try {
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

            Log.v("geocode","before json");
            JSONObject jsonObject;
            try {
                jsonObject = new JSONObject(stringBuilder.toString());

                userLng = ((JSONArray) jsonObject.get("results")).getJSONObject(0)
                        .getJSONObject("geometry").getJSONObject("location")
                        .getDouble("lng");

                userLat = ((JSONArray) jsonObject.get("results")).getJSONObject(0)
                        .getJSONObject("geometry").getJSONObject("location")
                        .getDouble("lat");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            Log.v("geocode", "done");

            queryThread.start();
        }
    };

    Thread queryThread = new Thread() {
        public void run() {

            try {
                int i = 0;
                while (userLat == 0.0 && userLng == 0.0 && i < 60) {
                    try {
                        // Wait a second
                        Thread.sleep(1000);
                        Log.v("test", "busy waiting one second, list size is " + nearestProperties.size());
                        i++;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } finally {
                try {
                    sendRequest("SELECT Name, Id, Location__Latitude__s, Location__Longitude__s, Available_Couches__c, Total_Couches__c\n" +
                            "FROM Property__c\n" +
                            "ORDER BY DISTANCE(Location__c, GEOLOCATION(" + userLat + "," + userLng + "), 'km') ASC\n" +
                            "LIMIT 10");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

                transitionThread.start();
            }
        }
    };

    Thread transitionThread  = new Thread() {
        public void run() {
            try {
                int i = 0;
                while (nearestProperties.size() == 0 && i < 20) {
                    try {
                        // Wait a second
                        Thread.sleep(1000);
                        Log.v("test", "busy waiting one second, list size is " + nearestProperties.size());
                        i++;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            finally {
                if (nearestProperties.size() != 0) {
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
            }
        }
    };

	private void sendRequest(String soql) throws UnsupportedEncodingException {
		RestRequest restRequest = RestRequest.getRequestForQuery(getString(R.string.api_version), soql);

        Log.v("connection", "before");

		client.sendAsync(restRequest, new RestClient.AsyncRequestCallback() {
            @Override
            public void onSuccess(RestRequest request, RestResponse result) {
                try {
                    nearestProperties.clear();
                    JSONArray records = result.asJSONObject().getJSONArray("records");
                    for (int i = 0; i < records.length(); i++) {
                        JSONObject record = records.getJSONObject(i);
                        Properties Properties = new Properties(record.getString("Name"), record.getString("Id"), record.getDouble("Location__Latitude__s"), record.getDouble("Location__Longitude__s"), record.getDouble("Available_Couches__c"), record.getDouble("Total_Couches__c"));
                        nearestProperties.add(Properties);
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

    class Properties {
        public String name;
        public String id;
        public double lat;
        public double lng;
        public double available;
        public double total;

        public Properties(String name, String id, double lat, double lng, double available, double total) {
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
