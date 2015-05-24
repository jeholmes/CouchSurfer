package com.jeholmes.couchsurf;

import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class MapsActivity extends FragmentActivity {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.

    double userLat;
    double userLng;

    private Marker[] markers;
    private Properties[] nearestProperties;
    private int total;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.map);

        Bundle extras = getIntent().getExtras();
        total = extras.getInt("total");
        markers = new Marker[total];
        nearestProperties = new Properties[total];

        userLat = extras.getDouble("lat");
        userLng = extras.getDouble("lng");

        for (int i = 0; i < total; i++) {
            nearestProperties[i] = new Properties(extras.getString(i + "-name"),
                    extras.getString(i + "-id"),
                    extras.getDouble(i + "-lat"),
                    extras.getDouble(i + "-lng"));
        }

        setUpMapIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        //mMap.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title("Marker"));

        for (int i = 0; i < total; i++) {
            markers[i] = mMap.addMarker(new MarkerOptions()
                    .title(nearestProperties[i].name)
                    .snippet(nearestProperties[i].id)
                    .position(new LatLng( nearestProperties[i].lat, nearestProperties[i].lng)));
        }

        mMap.setMyLocationEnabled(true);
        mMap.setOnMyLocationChangeListener(myLocationChangeListener);

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(userLat, userLng), 14.5f));
    }

    private GoogleMap.OnMyLocationChangeListener myLocationChangeListener = new GoogleMap.OnMyLocationChangeListener() {
        @Override
        public void onMyLocationChange(Location location) {
            LatLng loc = new LatLng(location.getLatitude(), location.getLongitude());

            /*Toast.makeText(MainActivity.this,
                    "Current location: " + loc.latitude + loc.longitude,
                    Toast.LENGTH_LONG).show();

            if(mMap != null){
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 16.0f));
                mMap.setOnMyLocationChangeListener(null);
            }*/

            userLat = loc.latitude;
            userLng = loc.longitude;
        }
    };

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