package com.jeholmes.couchsurf;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;
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
import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends SalesforceActivity {

    private RestClient client;

    // Boolean flags to track update queries
    boolean memberDone;
    boolean propertyDone;
    boolean geocodeDone;
    boolean couchesDone;

    // Variables to hold ids of created records
    String memberId;
    String propertyId;

    Dialog loadingDialog;

    /**
     * Override back button to start a new MainActivity with the address bundled as an extra
     */
    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK))
        {
            Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register);
    }

    @Override
    @SuppressLint("InflateParams") // To pass null to layout inflater
    public void onResume(RestClient client) {
        this.client = client;

        // Build loading dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(RegisterActivity.this);
        LayoutInflater inflater = RegisterActivity.this.getLayoutInflater();
        builder.setView(inflater.inflate(R.layout.loaddialog, null));
        loadingDialog = builder.create();
        loadingDialog.setCanceledOnTouchOutside(true);

        // Initialize couches number picker
        NumberPicker np = (NumberPicker) findViewById(R.id.couches);
        np.setMaxValue(10);
        np.setMinValue(1);
        np.setWrapSelectorWheel(false);
    }

    /**
     * Logout button click handler, logs out salesforce user
     */
    public void onLogoutClick(View v) {
        SalesforceSDKManager.getInstance().logout(this);
    }

    /**
     * Update button click handler, executes update request task
     */
    public void onUpdateClick(View v) {
        memberDone = false;
        propertyDone = false;
        geocodeDone = false;
        couchesDone = false;
        new updateTask().execute();
    }

    /**
     * Starts MainActivity and finishes this one
     */
    private void sendTransitionIntent() {
        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    /**
     * Geocodes the user address using Google Maps' geocode API
     */
    private LatLng geocodeAddress (String address) {
        Double tempLng = 0.0;
        Double tempLat = 0.0;

        Log.v("geocode", "defining http url");
        HttpGet httpGet = null;
        try {
            // Build http url for get request
            httpGet = new HttpGet(
                    "http://maps.google.com/maps/api/geocode/json?address=" +
                            URLEncoder.encode(address + ", BC, Canada", "UTF-8") +
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
            tempLng = ((JSONArray) jsonObject.get("results")).getJSONObject(0)
                    .getJSONObject("geometry").getJSONObject("location")
                    .getDouble("lng");

            // Extract latitude element
            tempLat = ((JSONArray) jsonObject.get("results")).getJSONObject(0)
                    .getJSONObject("geometry").getJSONObject("location")
                    .getDouble("lat");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Log.v("geocode", "done");

        return new LatLng(tempLat,tempLng);
    }

    /**
     * Sends soql query request
     * @param soql soql query string
     * @param type int to distinguish what table the query is called on
     */
    private void sendRequest(String soql, final int type) throws UnsupportedEncodingException {
        RestRequest restRequest = RestRequest.getRequestForQuery(getString(R.string.api_version), soql);

        // Send Async query request
        client.sendAsync(restRequest, new RestClient.AsyncRequestCallback() {
            // If request is successful then convert response records to appropriate objects
            @Override
            public void onSuccess(RestRequest request, RestResponse result) {
                try {
                    JSONArray records = result.asJSONObject().getJSONArray("records");
                    for (int i = 0; i < records.length(); i++) {
                        JSONObject record = records.getJSONObject(i);

                        if (type == 0) {
                            memberId = record.getString("Id");
                        } else if (type == 1) {
                            propertyId = record.getString("Id");
                        }
                    }
                    Log.v("connection", "success");
                } catch (Exception e) {
                    Log.v("connection", "failure");
                    onError(e);
                }
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(RegisterActivity.this,
                        RegisterActivity.this.getString(SalesforceSDKManager.getInstance().getSalesforceR().stringGenericError(), exception.toString()),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Sends soql update request
     * @param fields fields to update
     * @param type int to distinguish different record types
     */
    private void saveData(Map<String, Object> fields, final int type) {
        RestRequest restRequest;
        String recordType;

        // Translate type int to appropriate string
        if (type == 0){
            recordType = "Member__c";
        } else if (type == 1) {
            recordType = "Property__c";
        } else if (type == 2){
            recordType = "Couch__c";
        } else {
            recordType = null;
        }

        // Set rest request as update
        try {
            restRequest = RestRequest.getRequestForCreate(getString(R.string.api_version), recordType, fields);
        } catch (Exception e) {
            Toast.makeText(RegisterActivity.this,
                    RegisterActivity.this.getString(SalesforceSDKManager.getInstance().getSalesforceR().stringGenericError(), e.toString()),
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Send Async request
        client.sendAsync(restRequest, new RestClient.AsyncRequestCallback() {
            // If successful then set couch updated flag based on index
            @Override
            public void onSuccess(RestRequest request, RestResponse result) {
                try {
                    Toast.makeText(RegisterActivity.this,
                            "record added",
                            Toast.LENGTH_LONG).show();
                    if (type == 0) {
                        memberDone = true;
                    } else if (type == 1) {
                        propertyDone = true;
                    } else if (type == 2) {
                        couchesDone = true;
                    }
                } catch (Exception e) {
                    onError(e);
                }
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(RegisterActivity.this,
                        RegisterActivity.this.getString(SalesforceSDKManager.getInstance().getSalesforceR().stringGenericError(), exception.toString()),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Displays alert dialog if query was unable to receive records
     */
    private void queryFailed () {
        loadingDialog.dismiss();
        AlertDialog.Builder builder = new AlertDialog.Builder(RegisterActivity.this);
        builder.setTitle("Connection Failed");
        builder.setMessage("Could not query Salesforce");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                finish();
            }
        });
        Dialog alertDialog = builder.create();
        alertDialog.setCanceledOnTouchOutside(true);
        alertDialog.show();
    }

    /**
     * Displays alert dialog if fields are empty
     */
    private void emptyField (int flag) {
        loadingDialog.dismiss();
        AlertDialog.Builder builder = new AlertDialog.Builder(RegisterActivity.this);
        builder.setTitle("Please enter values for:");

        // Build message body based on flag set for fields missing
        String message = "";
        if (flag >= 4) {
            flag -= 4;
            message += "- Name\n";
        }
        if (flag >= 2) {
            flag -= 2;
            message += "- Address\n";
        }
        if (flag == 1) {
            message += "- Email\n";
        }
        message = message.substring(0,message.length()-1);

        builder.setMessage(message);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
            }
        });
        Dialog alertDialog = builder.create();
        alertDialog.setCanceledOnTouchOutside(true);
        alertDialog.show();
    }

    /**
     * AsyncTask to handle updating couches
     */
    private class updateTask extends AsyncTask<Void,Void,Integer > {

        String member;
        String address;
        String email;
        Double lat;
        Double lng;
        int couches;

        protected void onPreExecute() {
            // Extract values from fields in view
            EditText memberField = (EditText) findViewById(R.id.member_name);
            member = memberField.getText().toString();
            EditText addressField = (EditText) findViewById(R.id.address);
            address = addressField.getText().toString();
            EditText emailField = (EditText) findViewById(R.id.email);
            email = emailField.getText().toString();
            NumberPicker couchesField = (NumberPicker) findViewById(R.id.couches);
            couches = couchesField.getValue();


            // Initialize ids
            memberId = "";
            propertyId = "";

            // Initialize coordinates to 0.0 0.0;
            lat = 0.0;
            lng = 0.0;

            loadingDialog.show();
        }

        protected Integer doInBackground(Void... params) {

            // Check if fields have values and return fail if empty
            if (member.equals("") || address.equals("") || email.equals("")) {
                int returnValue = 1;
                if (email.equals("")) returnValue += 1;
                if (address.equals("")) returnValue += 2;
                if (member.equals("")) returnValue += 4;
                return returnValue;
            }

            // Add member
            Map<String, Object> memberFields = new HashMap<>();
            memberFields.put("Name", member);
            memberFields.put("Email__c", email);
            saveData(memberFields, 0);

            Log.v("update", "member name: " + member);
            int i = 0;
            while ( !memberDone && i < getResources().getInteger(R.integer.timeout)) {
                try {
                    // Wait a second
                    Thread.sleep(1000);
                    Log.v("busy wait", "waiting one second");
                    i++;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Log.v("update", "member done: " + memberDone);

            // Get member id
            try {
                sendRequest("SELECT Id FROM Member__c WHERE Name = '" + member + "'",0);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            Log.v("update","member id: " + memberId);
            i = 0;
            while ( memberId.equals("") && i < getResources().getInteger(R.integer.timeout)) {
                try {
                    // Wait a second
                    Thread.sleep(1000);
                    Log.v("busy wait", "waiting one second");
                    i++;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Log.v("update","member id: " + memberId);

            // Geocode address
            LatLng location = geocodeAddress(address);
            if (location.latitude != 0.0 && location.longitude != 0.0) {
                lat = location.latitude;
                lng = location.longitude;
                geocodeDone = true;
            }

            Log.v("update","property location: " + lat + " " + lng);

            // Add property
            Map<String, Object> propertyFields = new HashMap<>();
            propertyFields.put("Name", address);
            propertyFields.put("Location__Latitude__s", lat);
            propertyFields.put("Location__Longitude__s", lng);
            propertyFields.put("Member_del__c", memberId);
            propertyFields.put("Available_Couches__c", 0);
            propertyFields.put("Total_Couches__c", 0);
            propertyFields.put("Email__c", email);
            saveData(propertyFields, 1);

            Log.v("update", "property address: " + address);
            i = 0;
            while ( !propertyDone && i < getResources().getInteger(R.integer.timeout)) {
                try {
                    // Wait a second
                    Thread.sleep(1000);
                    Log.v("busy wait", "waiting one second");
                    i++;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Log.v("update", "property done: " + propertyDone);

            // Get property id
            try {
                sendRequest("SELECT Id FROM Property__c WHERE Name = '" + address + "'",1);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            Log.v("update","property id: " + propertyId);
            i = 0;
            while ( propertyId.equals("") && i < getResources().getInteger(R.integer.timeout)) {
                try {
                    // Wait a second
                    Thread.sleep(1000);
                    Log.v("busy wait", "waiting one second");
                    i++;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Log.v("update", "property id: " + propertyId);

            // Add couches
            for (int j = 0; j < couches; j++) {
                Map<String, Object> couchesFields = new HashMap<>();
                couchesFields.put("Property__c", propertyId);
                couchesFields.put("Email__c", email);
                saveData(couchesFields, 2);
            }

            Log.v("update","couches done: " + couchesDone);
            i = 0;
            while ( !couchesDone && i < getResources().getInteger(R.integer.timeout)) {
                try {
                    // Wait a second
                    Thread.sleep(1000);
                    Log.v("busy wait", "waiting one second");
                    i++;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Log.v("update","couches done: " + couchesDone);

            if (memberDone && propertyDone && geocodeDone && couchesDone) {
                return 0;
            } else {
                return 1;
            }
        }

        protected void onPostExecute(Integer result) {
            if (result == 0) {
                loadingDialog.dismiss();

                // If successful then execute task to transition back to MapsActivity
                sendTransitionIntent();
            } else if (result == 1){
                loadingDialog.dismiss();

                // Else display alert dialog
                queryFailed();
            } else {
                loadingDialog.dismiss();

                emptyField(result-1);
            }
        }
    }
}
