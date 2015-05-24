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
import android.view.View;
import android.widget.Toast;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.ui.sfnative.SalesforceActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

/**
 * Main activity
 */
public class MainActivity extends SalesforceActivity /*implements OnItemClickListener*/ {

    private RestClient client;
    private ArrayList<Properties> nearestProperties;

	double userLat;
	double userLng;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Setup view
		setContentView(R.layout.main);
	}
	
	@Override 
	public void onResume() {
		// Hide everything until we are logged in
		findViewById(R.id.root).setVisibility(View.INVISIBLE);

        // FIX THIS
        userLat = 49.2574123;
        userLng = -123.04574;

        nearestProperties = new ArrayList<>();

		/*ListView listView = (ListView) findViewById(R.id.contacts_list);
		// Create list adapter
		nearestProperties = new ArrayAdapter<Properties>(this, android.R.layout.simple_list_item_1, new ArrayList<Properties>());
		listView.setAdapter(nearestProperties);
        listView.setOnItemClickListener(this);*/

		super.onResume();
	}

    /*public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Properties Properties = nearestProperties.getItem(position);
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("id", Properties.id);
        intent.putExtra("name", Properties.name);
        intent.putExtra("lat", Properties.lat);
        intent.putExtra("lng", Properties.lng);
        startActivity(intent);
    }*/
	
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

	public void onMapClick(View v) throws UnsupportedEncodingException {
		queryThread.start();
        transitionThread.start();
	}


    Thread queryThread = new Thread() {
        public void run() {
            try {
                fetchProperties();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    };

    Thread transitionThread  = new Thread() {
        public void run() {
            try {
                while (nearestProperties.size() == 0) {
                    try {
                        // Wait a second
                        Thread.sleep(1000);
                        Log.v("test", "busy waiting one second, list size is " + nearestProperties.size());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            finally {
                Intent intent = new Intent(MainActivity.this, MapsActivity.class);
                intent.putExtra("total", nearestProperties.size());
                intent.putExtra("lat", userLat);
                intent.putExtra("lng", userLng);
                for (int i = 0; i < nearestProperties.size(); i++) {
                    intent.putExtra(i + "-id", nearestProperties.get(i).id);
                    intent.putExtra(i + "-name", nearestProperties.get(i).name);
                    intent.putExtra(i + "-lat", nearestProperties.get(i).lat);
                    intent.putExtra(i + "-lng", nearestProperties.get(i).lng);
                }
                startActivity(intent);
            }
        }
    };

    public void onSearchClick (View v) {
        quickToast("Lat: " + userLat + ", Long: " + userLng);
    }

    public void onMyLocationClick (View v) throws UnsupportedEncodingException {

        // Get Location Manager and check for GPS & Network location services
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            // Build the alert dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Location Services Not Active");
            builder.setMessage("Please enable Location Services and GPS");
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialogInterface, int i) {
                    // Show location settings when the user acknowledges the alert dialog
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialogInterface, int i) {
                    // DO nothing
                }
            });
            Dialog alertDialog = builder.create();
            alertDialog.setCanceledOnTouchOutside(false);
            alertDialog.show();
        }

        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.
                userLat = location.getLatitude();
                userLng = location.getLongitude();
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}

            public void onProviderEnabled(String provider) {}

            public void onProviderDisabled(String provider) {}
        };

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);

        //fetchProperties();

        quickToast("Lat: " + userLat + ", Long: " + userLng);
    }

	public void fetchProperties() throws UnsupportedEncodingException {
        sendRequest("SELECT Name, Id, Location__Latitude__s, Location__Longitude__s\n" +
                "FROM Property__c\n" +
                "ORDER BY DISTANCE(Location__c, GEOLOCATION(" + userLat + "," + userLng + "), 'km') ASC\n" +
                "LIMIT 10");

        //quickToast("Retrn list size: " + nearestProperties.size());
    }

	private void sendRequest(String soql) throws UnsupportedEncodingException {
		RestRequest restRequest = RestRequest.getRequestForQuery(getString(R.string.api_version), soql);

		client.sendAsync(restRequest, new RestClient.AsyncRequestCallback() {
            @Override
            public void onSuccess(RestRequest request, RestResponse result) {
                try {
                    nearestProperties.clear();
                    JSONArray records = result.asJSONObject().getJSONArray("records");
                    for (int i = 0; i < records.length(); i++) {
                        JSONObject record = records.getJSONObject(i);
                        Properties Properties = new Properties(record.getString("Name"), record.getString("Id"), record.getDouble("Location__Latitude__s"), record.getDouble("Location__Longitude__s"));
                        nearestProperties.add(Properties);
                    }
                } catch (Exception e) {
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

    private void quickToast(String message) {
        Toast.makeText(MainActivity.this,
                message,
                Toast.LENGTH_LONG).show();
    }

    class Properties {
        public String name;
        public String id;
        public double lat;
        public double lng;

        public Properties(String name, String id, double lat, double lng) {
            this.name = name;
            this.id = id;
            this.lat = lat;
            this.lng = lng;
        }

        public String toString() {
            return name;
        }
    }
}
