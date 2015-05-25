package com.jeholmes.couchsurf;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
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
import java.util.HashMap;
import java.util.Map;

public class DetailActivity extends SalesforceActivity {

    private RestClient client;

    private ArrayList<Couch> returnedCouches;
    private ArrayList<Member> returnedMembers;

    private ArrayList<Couch> couchesToUpdate;

    private String propertyId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.detail);

        TextView nameField = (TextView) findViewById(R.id.property_name);
        //idField = (TextView) findViewById(R.id.property_id);

        Bundle extras = getIntent().getExtras();
        nameField.setText(extras.getString("name"));
        propertyId = extras.getString("propertyId");
        int totalCouches = (int) Float.parseFloat(extras.getString("total"));
        int availableCouches = (int) Float.parseFloat(extras.getString("avail"));

        TextView totalField = (TextView) findViewById(R.id.property_total);
        totalField.setText(totalCouches + "");
        TextView availField = (TextView) findViewById(R.id.property_avail);
        availField.setText(availableCouches + "");

        returnedCouches = new ArrayList<>();
        returnedMembers = new ArrayList<>();
        couchesToUpdate = new ArrayList<>();

        /*
        couchesQueryThread.start();
        membersQueryThread.start();

        int i = 0;
        while ((returnedCouches.size() == 0 || returnedMembers.size() == 0) && i < 15) {
            try {
                // Wait a second
                Thread.sleep(1000);
                Log.v("test", "busy waiting one second, couch list size: " + returnedCouches.size() + " , member list size: " + returnedMembers.size());
                i++;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        //Populate view

        */
    }

    Thread updateThread = new Thread () {
        public void run() {
            for (int i = 0; i < couchesToUpdate.size(); i++) {
                Map<String, Object> fields = new HashMap<>();
                fields.put("Member__c", couchesToUpdate.get(i).memberId);
                fields.put("Member__c", couchesToUpdate.get(i).vacancy);
                saveData(couchesToUpdate.get(i).couchId, fields);
            }
        }
    };

    @Override
    public void onResume(RestClient client) {
        this.client = client;
    }

    Thread couchesQueryThread = new Thread() {
        public void run() {
                try {
                    sendRequest("SELECT Id, Vacancy__c FROM Couch__c WHERE Property__r.Id = " + propertyId + " AND Vacancy__c = true", "couch");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
        }
    };

    Thread membersQueryThread = new Thread() {
        public void run() {
            try {
                sendRequest("SELECT Id, Name FROM Member__c", "member");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    };


    public void onLogoutClick(View v) {
        SalesforceSDKManager.getInstance().logout(this);
    }

    public void onUpdateClick(View v) {
        //updateThread.start();
    }

    private void saveData(String id, Map<String, Object> fields) {
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
                    DetailActivity.this.finish();
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

    private void sendRequest(String soql, final String object) throws UnsupportedEncodingException {
        RestRequest restRequest = RestRequest.getRequestForQuery(getString(R.string.api_version), soql);

        Log.v("connection", "before");

        client.sendAsync(restRequest, new RestClient.AsyncRequestCallback() {
            @Override
            public void onSuccess(RestRequest request, RestResponse result) {
                try {
                    if (object.equals("couch")) {
                        returnedCouches.clear();
                    } else if (object.equals("couch"))  {
                        returnedMembers.clear();
                    }

                    JSONArray records = result.asJSONObject().getJSONArray("records");
                    for (int i = 0; i < records.length(); i++) {
                        JSONObject record = records.getJSONObject(i);
                        if (object.equals("member")) {
                            Couch couch = new Couch(record.getString("Id"), record.getBoolean("Vacancy__c"), record.getString("Member__c"));
                            returnedCouches.add(couch);
                        } else if (object.equals("member"))  {
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

        public Couch(String couchId, boolean vacancy, String memberId) {
            this.couchId = couchId;
            this.vacancy = vacancy;
            this.memberId = memberId;
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

}
