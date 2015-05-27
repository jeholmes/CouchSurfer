package com.jeholmes.couchsurf;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class DetailActivity extends SalesforceActivity {

    private RestClient client;

    // Lists to hold returned query records
    private ArrayList<Couch> returnedCouches;
    private ArrayList<Member> returnedMembers;
    private ArrayList<Property> nearestProperties;

    // Device id used as update signature
    private String deviceId;

    // Property information
    private String propertyId;
    protected int totalCouches;
    protected int availableCouches;

    // Variables for verifying update success
    private boolean[] couchUpdateDone;
    private boolean couchesDone;

    // User variables
    double userLat;
    double userLng;
    String userAddress = "";

    Dialog loadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.detail);
    }

    @SuppressLint("InflateParams") // To pass null to layout inflater
    @Override
    public void onResume(RestClient client) {
        this.client = client;

        // Build loading dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(DetailActivity.this);
        LayoutInflater inflater = DetailActivity.this.getLayoutInflater();
        builder.setView(inflater.inflate(R.layout.loaddialog, null));
        loadingDialog = builder.create();
        loadingDialog.setCanceledOnTouchOutside(true);

        // Extract information from bundle and update property name in view
        TextView nameField = (TextView) findViewById(R.id.property_name);
        Bundle extras = getIntent().getExtras();
        nameField.setText(extras.getString("name"));
        propertyId = extras.getString("propertyId");
        totalCouches = (int) Float.parseFloat(extras.getString("total"));
        availableCouches = (int) Float.parseFloat(extras.getString("avail"));


        // Update couch information in view
        TextView totalField = (TextView) findViewById(R.id.property_total);
        totalField.setText(totalCouches + "");
        TextView availField = (TextView) findViewById(R.id.property_avail);
        availField.setText(availableCouches + "");

        // Extract user variables
        userLat = extras.getDouble("lat");
        userLng = extras.getDouble("lng");
        userAddress = extras.getString("address");

        // Set device id from system variable
        deviceId = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);

        // Initialize query return lists
        returnedCouches = new ArrayList<>();
        returnedMembers = new ArrayList<>();
        nearestProperties = new ArrayList<>();

        // Set query strings to get couches and members
        String couchQuery = "SELECT Id, Vacancy__c, Member__c, device_Id__c FROM Couch__c WHERE Property__r.Id='"
                + propertyId +"'";
        String memberQuery = "SELECT Id, Name FROM Member__c";

        new populateTask().execute(couchQuery, memberQuery);
    }

    /**
     * Logout button click handler, logs out salesforce user
     */
    public void onLogoutClick(View v) {
        SalesforceSDKManager.getInstance().logout(this);
    }

    /**
     * Update button click handler, executes couch update request task
     */
    public void onUpdateClick(View v) {
        new updateTask().execute();
    }

    /**
     * Starts MapActivity with property information from list bundled as extras
     */
    private void sendTransitionIntent() {
        Intent intent = new Intent(DetailActivity.this, MapsActivity.class);
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
     * Sends query to get nearest properties based on user coordinates and waits for response
     */
    private void sendQuery() {
        // Attempt to send query
        try {
            sendRequest("SELECT Name, Id, Location__Latitude__s, Location__Longitude__s, Available_Couches__c, Total_Couches__c\n" +
                    "FROM Property__c\n" +
                    "ORDER BY DISTANCE(Location__c, GEOLOCATION(" + userLat + "," + userLng + "), 'km') ASC\n" +
                    "LIMIT 10");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        // Wait for response to populate property list
        busyWait(30, nearestProperties.size() == 0);
    }

    /**
     * Sends soql query request
     * @param soql soql query string
     */
    private void sendRequest(String soql) throws UnsupportedEncodingException {
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

                        if (record.has("Vacancy__c")) {
                            Couch couch = new Couch(record.getString("Id"),
                                    record.getBoolean("Vacancy__c"),
                                    record.getString("Member__c"),
                                    record.getString("device_Id__c"), false);
                            returnedCouches.add(couch);
                        } else if (record.has("Location__Latitude__s") || record.has("Location__Longitude__s")) {
                            Property properties = new Property(record.getString("Name"),
                                    record.getString("Id"),
                                    record.getDouble("Location__Latitude__s"),
                                    record.getDouble("Location__Longitude__s"),
                                    record.getDouble("Available_Couches__c"),
                                    record.getDouble("Total_Couches__c"));
                            nearestProperties.add(properties);
                        } else {
                            Member member = new Member(record.getString("Name"),
                                    record.getString("Id"));
                            returnedMembers.add(member);
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
                Toast.makeText(DetailActivity.this,
                        DetailActivity.this.getString(SalesforceSDKManager.getInstance().getSalesforceR().stringGenericError(), exception.toString()),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Sends soql update request
     * @param id id of object to update
     * @param fields fields to update
     * @param index index of couch to set update flag
     */
    private void saveData(final String id, Map<String, Object> fields, final int index) {
        RestRequest restRequest;

        // Set rest request as update
        try {
            restRequest = RestRequest.getRequestForUpdate(getString(R.string.api_version), "Couch__c", id, fields);
        } catch (Exception e) {
            Toast.makeText(DetailActivity.this,
                    DetailActivity.this.getString(SalesforceSDKManager.getInstance().getSalesforceR().stringGenericError(), e.toString()),
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Send Async request
        client.sendAsync(restRequest, new RestClient.AsyncRequestCallback() {
            // If successful then set couch updated flag based on index
            @Override
            public void onSuccess(RestRequest request, RestResponse result) {
                try {
                    Toast.makeText(DetailActivity.this,
                            id + " updated",
                            Toast.LENGTH_LONG).show();

                    if (!couchesDone) {
                        couchUpdateDone[index] = true;
                    }
                } catch (Exception e) {
                    onError(e);
                }
            }

            @Override
            public void onError(Exception exception) {
                Toast.makeText(DetailActivity.this,
                        DetailActivity.this.getString(SalesforceSDKManager.getInstance().getSalesforceR().stringGenericError(), exception.toString()),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Displays alert dialog if query was unable to receive records
     */
    private void queryFailed () {
        loadingDialog.dismiss();
        AlertDialog.Builder builder = new AlertDialog.Builder(DetailActivity.this);
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
     * Busy wait based on conditional
     * @param seconds time out limit
     * @param condition condition statement to check
     */
    private void busyWait(int seconds, boolean condition) {
        int i = 0;
        while ( condition && i < seconds) {
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

    /**
     * AsyncTask to handle querying for couches and members
     */
    private class populateTask extends AsyncTask<String,Void,Boolean > {

        protected void onPreExecute() {
            loadingDialog.show();
        }

        protected Boolean doInBackground(String... queries) {
            // Send request for each query
            for (String query : queries) {
                try {
                    sendRequest(query);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }

            // Wait for couch and member arrays to populate with returned records
            busyWait(30, (returnedCouches.size() == 0 || returnedMembers.size() == 0));

            // Return if arrays are populated
            return returnedCouches.size() != 0 && returnedMembers.size() != 0;
        }

        @SuppressLint("InflateParams")
        protected void onPostExecute(Boolean result) {
            if (result) {
                // Build spinner adapters
                ArrayAdapter<String> spinnerAdapter;
                ArrayAdapter<String> occupiedAdapter;
                String[] memberNames = new String[returnedMembers.size() + 1];
                final String[] memberIds = new String[returnedMembers.size() + 1];
                memberIds[0] = "";
                memberNames[0] = "";
                String[] occupied = {"OCCUPIED"};
                for (int i = 0; i < returnedMembers.size(); i++) {
                    memberIds[i+1] = returnedMembers.get(i).id;
                    memberNames[i+1] = returnedMembers.get(i).name;
                }
                spinnerAdapter = new ArrayAdapter<>(DetailActivity.this, android.R.layout.simple_spinner_item, memberNames);
                occupiedAdapter = new ArrayAdapter<>(DetailActivity.this, android.R.layout.simple_spinner_item, occupied);

                // Inflate spinner layout for each couch returned
                LinearLayout item = (LinearLayout) findViewById(R.id.spinner_group);
                for (int i = 0; i < returnedCouches.size(); i++) {
                    // Set boolean definitions
                    boolean hasMemberId = !returnedCouches.get(i).memberId.equals("") &&
                            !returnedCouches.get(i).memberId.equals("null");
                    boolean hasDeviceId = returnedCouches.get(i).deviceId.equals(deviceId);

                    // Set spinner object
                    View child = getLayoutInflater().inflate(R.layout.dropdown, null);
                    Spinner spinner = (Spinner) child.findViewById(R.id.spinner);

                    // Set item select listener
                    final int finalI = i;
                    spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        // On selected get member id that corresponds to name and set update flag
                        @Override
                        public void onItemSelected(AdapterView<?> arg0, View arg1,
                                                   int arg2, long arg3) {
                            returnedCouches.get(finalI).memberId = memberIds[arg2];
                            returnedCouches.get(finalI).toUpdate = true;
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> arg0) {
                            returnedCouches.get(finalI).toUpdate = false;
                        }
                    });

                    if (hasMemberId && hasDeviceId) { // If couch is occupied by user
                        spinner.setAdapter(spinnerAdapter);

                        // Set selection
                        String memberId = returnedCouches.get(i).memberId;
                        int memberIndex = Arrays.asList(memberIds).indexOf(memberId);
                        String memberName = memberNames[memberIndex];
                        spinner.setSelection(spinnerAdapter.getPosition(memberName));

                        spinner.setEnabled(true);
                    } else if (!hasDeviceId && hasMemberId) { //Else if occupied by someone else
                        spinner.setAdapter(occupiedAdapter);

                        // Set background to grey
                        spinner.setBackground(getResources().getDrawable(R.drawable.rounded_occupied_spinner));

                        spinner.setEnabled(false);
                    } else { // Else couch is vacant
                        spinner.setAdapter(spinnerAdapter);
                        spinner.setEnabled(true);
                    }
                    // Add spinner to view
                    item.addView(child);
                }
                loadingDialog.dismiss();

            } else { // Query failed to get couches and members
                loadingDialog.dismiss();

                // Display alert dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(DetailActivity.this);
                builder.setTitle("Connection Failed");
                builder.setMessage("Could not query couches");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                });
                Dialog alertDialog = builder.create();
                alertDialog.setCanceledOnTouchOutside(true);
                alertDialog.show();
            }
        }
    }

    /**
     * AsyncTask to handle updating couches
     */
    private class updateTask extends AsyncTask<Void,Void,Boolean > {

        protected void onPreExecute() {
            loadingDialog.show();
        }

        protected Boolean doInBackground(Void... params) {
            // Initialize update flags
            int couchesToUpdate = 0;
            couchesDone = false;

            // Iterate through all couches of property
            for (int i = 0; i < returnedCouches.size(); i++) {
                if (returnedCouches.get(i).toUpdate) { // If couch is flagged to update
                    Log.v("couch update", returnedCouches.get(i).couchId);
                    Map<String, Object> fields = new HashMap<>();

                    // Add device id and member id fields according to information to update
                    if (returnedCouches.get(i).memberId.equals("") || returnedCouches.get(i).memberId.equals("null")) {
                        fields.put("device_Id__c", "");
                        fields.put("Member__c", "");
                    } else {
                        fields.put("device_Id__c", deviceId);
                        fields.put("Member__c", returnedCouches.get(i).memberId);
                    }

                    // Send update request for the couch
                    String couchID = returnedCouches.get(i).couchId;
                    saveData(couchID, fields, couchesToUpdate);

                    // Increment flag for update completion
                    couchesToUpdate++;
                }
            }

            // Initialize couch update flag array
            couchUpdateDone = new boolean[couchesToUpdate];
            for (int i = 0; i < couchesToUpdate; i++) {
                couchUpdateDone[i] = false;
            }

            // Wait for couch update flag array to be all true
            int i = 0;
            while (!couchesDone && i < 30) {
                couchesDone = true;
                for (boolean flag : couchUpdateDone) {
                    if (!flag) {
                        couchesDone = false;
                    }
                }
                try {
                    Thread.sleep(1000);
                    i++;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            return couchesDone;
        }

        protected void onPostExecute(Boolean result) {
            if (result) {
                loadingDialog.dismiss();

                // If successful then execute task to transition back to updated MapsActivity
                new refreshMapTask().execute();
            } else {
                loadingDialog.dismiss();

                // Else display alert dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(DetailActivity.this);
                builder.setTitle("Failed");
                builder.setMessage("Failed to update");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                });
                Dialog alertDialog = builder.create();
                alertDialog.setCanceledOnTouchOutside(true);
                alertDialog.show();
            }
        }
    }

    /**
     * AsyncTask to handle querying for property updates and transitioning back to MapsActivity
     */
    private class refreshMapTask extends AsyncTask<Void,Void,Boolean > {

        protected void onPreExecute() {
            loadingDialog.show();
        }

        protected Boolean doInBackground(Void... params) {
            sendQuery();
            return (nearestProperties.size() != 0);
        }

        protected void onPostExecute(Boolean result) {
            if (result) {
                sendTransitionIntent();
            } else {
                queryFailed();
            }
        }
    }

    /**
     * Class definition for couch object
     */
    class Couch {
        public String couchId;
        public boolean vacancy;
        public String memberId;
        public String deviceId;
        public boolean toUpdate;

        public Couch(String couchId, boolean vacancy, String memberId, String deviceId, boolean toUpdate) {
            this.couchId = couchId;
            this.vacancy = vacancy;
            this.memberId = memberId;
            this.deviceId = deviceId;
            this.toUpdate = toUpdate;
        }

        public String toString() {
            return couchId;
        }
    }

    /**
     * Class definition for member object
     */
    class Member {
        public String name;
        public String id;

        public Member(String name, String id) {
            this.name = name;
            this.id = id;
        }

        public String toString() {
            return name;
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
