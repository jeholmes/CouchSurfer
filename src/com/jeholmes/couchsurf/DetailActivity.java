package com.jeholmes.couchsurf;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.rest.RestClient;
import com.salesforce.androidsdk.rest.RestRequest;
import com.salesforce.androidsdk.rest.RestResponse;
import com.salesforce.androidsdk.ui.sfnative.SalesforceActivity;

import java.util.HashMap;
import java.util.Map;

public class DetailActivity extends SalesforceActivity {

    private RestClient client;

    private String merchandiseId;
    private EditText nameField;
    private EditText lngField;
    private EditText latField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.detail);

        nameField = (EditText) findViewById(R.id.name_field);
        lngField = (EditText) findViewById(R.id.lng_field);
        latField = (EditText) findViewById(R.id.lat_field);

        Bundle extras = getIntent().getExtras();
        merchandiseId = extras.getString("id");
        nameField.setText(extras.getString("name"));
        lngField.setText(extras.getDouble("lng") + "");
        latField.setText(extras.getDouble("lat") + "");
    }

    @Override
    public void onResume(RestClient client) {
        this.client = client;
    }

    public void onLogoutClick(View v) {
        SalesforceSDKManager.getInstance().logout(this);
    }

    public void onUpdateClick(View v) {
        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("Name", nameField.getText().toString());
        fields.put("Location__Latitude__s", latField.getText().toString());
        fields.put("Location__Longitude__s", lngField.getText().toString());
        saveData(merchandiseId, fields);
    }

    private void saveData(String id, Map<String, Object> fields) {
        RestRequest restRequest;
        try {
            restRequest = RestRequest.getRequestForUpdate(getString(R.string.api_version), "Property__c", id, fields);
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

}
