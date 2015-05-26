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

    private ArrayList<Couch> returnedCouches;
    private ArrayList<Member> returnedMembers;

    private ArrayList<Properties> nearestProperties;

    private String propertyId;
    private String deviceId;

    protected int totalCouches;
    protected int availableCouches;

    private boolean[] couchUpdateDone;
    private boolean couchesDone;

    double userLat;
    double userLng;

    Dialog loadingDialog;

    @Override
    @SuppressLint("InflateParams")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.detail);

        AlertDialog.Builder builder = new AlertDialog.Builder(DetailActivity.this);
        LayoutInflater inflater = DetailActivity.this.getLayoutInflater();
        builder.setView(inflater.inflate(R.layout.load_dialog, null));
        loadingDialog = builder.create();
        loadingDialog.setCanceledOnTouchOutside(true);

        TextView nameField = (TextView) findViewById(R.id.property_name);

        Bundle extras = getIntent().getExtras();
        nameField.setText(extras.getString("name"));
        propertyId = extras.getString("propertyId");
        totalCouches = (int) Float.parseFloat(extras.getString("total"));
        availableCouches = (int) Float.parseFloat(extras.getString("avail"));

        userLat = extras.getDouble("lat");
        userLng = extras.getDouble("lng");


        deviceId = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);

        TextView totalField = (TextView) findViewById(R.id.property_total);
        totalField.setText(totalCouches + "");
        TextView availField = (TextView) findViewById(R.id.property_avail);
        availField.setText(availableCouches + "");

        returnedCouches = new ArrayList<>();
        returnedMembers = new ArrayList<>();
    }

    @Override
    public void onResume(RestClient client) {
        this.client = client;

        nearestProperties = new ArrayList<>();

        String couchQuery = "SELECT Id, Vacancy__c, Member__c, device_Id__c FROM Couch__c WHERE Property__r.Id='" + propertyId +"'";
        String memberQuery = "SELECT Id, Name FROM Member__c";

        new populateTask().execute(couchQuery, memberQuery);
    }

    private class updateTask extends AsyncTask<Void,Void,Boolean > {

        @SuppressLint("InflateParams")
        protected void onPreExecute() {

            loadingDialog.show();
        }

        protected Boolean doInBackground(Void... params) {
            int couchesToUpdate = 0;
            couchesDone = false;


            for (int i = 0; i < returnedCouches.size(); i++) {
                if (returnedCouches.get(i).toUpdate) {

                    Log.v("couch update", returnedCouches.get(i).couchId);
                    Map<String, Object> fields = new HashMap<>();

                    if (returnedCouches.get(i).memberId.equals("") || returnedCouches.get(i).memberId.equals("null")) {
                        fields.put("device_Id__c", "");
                        fields.put("Member__c", "");
                    } else {
                        fields.put("device_Id__c", deviceId);
                        fields.put("Member__c", returnedCouches.get(i).memberId);
                    }

                    String couchID = returnedCouches.get(i).couchId;
                    saveData(couchID, fields, couchesToUpdate);

                    couchesToUpdate++;
                }
            }
            couchUpdateDone = new boolean[couchesToUpdate];
            for (int i = 0; i < couchesToUpdate; i++) {
                couchUpdateDone[i] = false;
            }

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

        @SuppressLint("InflateParams")
        protected void onPostExecute(Boolean result) {
            if (result) {

                loadingDialog.dismiss();

                new refreshMapTask().execute();

            } else {
                loadingDialog.dismiss();

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

    private class populateTask extends AsyncTask<String,Void,Boolean > {

        @SuppressLint("InflateParams")
        protected void onPreExecute() {

            loadingDialog.show();
        }

        protected Boolean doInBackground(String... queries) {

            for (String query : queries) {
                try {
                    sendRequest(query);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }

            int i = 0;
            while ((returnedCouches.size() == 0 || returnedMembers.size() == 0) && i < 30) {
                try {
                    Thread.sleep(1000);
                    i++;
                    Log.v("test", "couches: " + returnedCouches.size());
                    Log.v("test", "members: " + returnedMembers.size());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            return returnedCouches.size() != 0 && returnedMembers.size() != 0;
        }

        @SuppressLint("InflateParams")
        protected void onPostExecute(Boolean result) {
            if (result) {

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

                LinearLayout item = (LinearLayout) findViewById(R.id.spinner_group);
                for (int i = 0; i < returnedCouches.size(); i++) {

                    boolean hasMemberId = !returnedCouches.get(i).memberId.equals("") && !returnedCouches.get(i).memberId.equals("null");
                    boolean hasDeviceId = returnedCouches.get(i).deviceId.equals(deviceId);

                    View child = getLayoutInflater().inflate(R.layout.dropdown, null);

                    Spinner spinner = (Spinner) child.findViewById(R.id.spinner);

                    final int finalI = i;
                    spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

                        @Override
                        public void onItemSelected(AdapterView<?> arg0, View arg1,
                                                   int arg2, long arg3) {
                            Log.v("memberId", memberIds[arg2] + "");

                            //if (!returnedCouches.get(finalI).memberId.equals("") && !returnedCouches.get(finalI).memberId.equals("null")) {
                                returnedCouches.get(finalI).memberId = memberIds[arg2];
                                returnedCouches.get(finalI).toUpdate = true;
                            //}



                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> arg0) {
                            returnedCouches.get(finalI).toUpdate = false;
                        }
                    });

                    if (hasMemberId && hasDeviceId) { // occupied by user
                        spinner.setAdapter(spinnerAdapter);

                        // Set selection
                        String memberId = returnedCouches.get(i).memberId;
                        Log.v("own couch " + i, "memberid: " + memberId);
                        int memberIndex = Arrays.asList(memberIds).indexOf(memberId);
                        Log.v("own couch " + i, "memberindex: " + memberIndex);
                        String memberName = memberNames[memberIndex];
                        Log.v("own couch " + i, "membername: " + memberName);
                        spinner.setSelection(spinnerAdapter.getPosition(memberName));

                        spinner.setEnabled(true);
                    } else if (!hasDeviceId && hasMemberId) { //occupied by someone else
                        spinner.setAdapter(occupiedAdapter);
                        //spinner.setAdapter(spinnerAdapter);
                        spinner.setBackground(getResources().getDrawable(R.drawable.rounded_occupied_spinner));
                        spinner.setEnabled(false);
                    } else { //vacant
                        spinner.setAdapter(spinnerAdapter);
                        spinner.setEnabled(true);
                    }

                    item.addView(child);
                }
                loadingDialog.dismiss();

            } else {
                loadingDialog.dismiss();

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

    private class refreshMapTask extends AsyncTask<Void,Void,Boolean > {

        protected void onPreExecute() {
            // Get Location Manager and check for GPS & Network location services
            loadingDialog.show();
        }

        protected Boolean doInBackground(Void... params) {

            waitAndSendQuery();

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

    private void waitAndSendQuery() {

        try {
            sendRequest("SELECT Name, Id, Location__Latitude__s, Location__Longitude__s, Available_Couches__c, Total_Couches__c\n" +
                    "FROM Property__c\n" +
                    "ORDER BY DISTANCE(Location__c, GEOLOCATION(" + userLat + "," + userLng + "), 'km') ASC\n" +
                    "LIMIT 10");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        int i = 0;
        while (nearestProperties.size() == 0 && i < 30) {
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

    private void queryFailed () {
        loadingDialog.dismiss();
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

    private void sendTransitionIntent() {
        Intent intent = new Intent(DetailActivity.this, MapsActivity.class);
        intent.putExtra("total", nearestProperties.size());
        intent.putExtra("lat", userLat);
        intent.putExtra("lng", userLng);
        intent.putExtra("address", "");
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

    public void onLogoutClick(View v) {
        SalesforceSDKManager.getInstance().logout(this);
    }

    public void onUpdateClick(View v) {
        new updateTask().execute();
    }

    private void saveData(final String id, Map<String, Object> fields, final int index) {
        RestRequest restRequest;
        try {
            restRequest = RestRequest.getRequestForUpdate(getString(R.string.api_version), "Couch__c", id, fields);
        } catch (Exception e) {
            Toast.makeText(DetailActivity.this,
                    DetailActivity.this.getString(SalesforceSDKManager.getInstance().getSalesforceR().stringGenericError(), e.toString()),
                    Toast.LENGTH_LONG).show();
            return;
        }

        client.sendAsync(restRequest, new RestClient.AsyncRequestCallback() {
            @Override
            public void onSuccess(RestRequest request, RestResponse result) {
                try {
                    //DetailActivity.this.finish();
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

    private void sendRequest(String soql) throws UnsupportedEncodingException {
        RestRequest restRequest = RestRequest.getRequestForQuery(getString(R.string.api_version), soql);

        client.sendAsync(restRequest, new RestClient.AsyncRequestCallback() {
            @Override
            public void onSuccess(RestRequest request, RestResponse result) {
                try {
                        //returnedCouches.clear();
                        //returnedMembers.clear();

                    JSONArray records = result.asJSONObject().getJSONArray("records");
                    for (int i = 0; i < records.length(); i++) {
                        JSONObject record = records.getJSONObject(i);

                        if (record.has("Vacancy__c")) {
                            Couch couch = new Couch(record.getString("Id"), record.getBoolean("Vacancy__c"), record.getString("Member__c"), record.getString("device_Id__c"), false);
                            returnedCouches.add(couch);
                        } else if (record.has("Location__Latitude__s") || record.has("Location__Longitude__s")) {
                            Properties Properties = new Properties(record.getString("Name"), record.getString("Id"), record.getDouble("Location__Latitude__s"), record.getDouble("Location__Longitude__s"), record.getDouble("Available_Couches__c"), record.getDouble("Total_Couches__c"));
                            nearestProperties.add(Properties);
                        } else {
                            Member member = new Member(record.getString("Name"), record.getString("Id"));
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
