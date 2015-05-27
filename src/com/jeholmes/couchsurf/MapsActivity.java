package com.jeholmes.couchsurf;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class MapsActivity extends FragmentActivity {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.

    // User variables
    double userLat;
    double userLng;
    String userAddress = "";

    // Arrays for transforming returned properties into markers
    private Property[] nearestProperties;
    private Marker[] markers;
    private int totalProperties;

    /**
     * Custom marker info window definition
     */
    @SuppressLint("InflateParams") // To pass null to layout inflater
    class MyInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {

        private final View myContentsView;

        // Set info window layout to be layout file
        MyInfoWindowAdapter(){
            myContentsView = getLayoutInflater().inflate(R.layout.windowlayout, null);
        }

        @Override
        public View getInfoContents(Marker marker) {

            // Get information from marker array
            final String title = marker.getTitle();
            final String[] lines = marker.getSnippet().split("\n");

            // Info windows consists of a title and a snippet that wrap content
            TextView tvTitle = ((TextView)myContentsView.findViewById(R.id.title));
            tvTitle.setText(title);
            TextView tvSnippet = ((TextView)myContentsView.findViewById(R.id.snippet));

            // Extract vacancy from snippet string
            String vacancy = lines[1];

            // Set snippet background based on vacancy
            switch (vacancy) {
                case "FULL": tvSnippet.setBackground(getResources().getDrawable(R.drawable.rounded_vacancy_red));
                    break;
                case "HALF FULL": tvSnippet.setBackground(getResources().getDrawable(R.drawable.rounded_vacancy_yellow));
                    break;
                case "OPEN": tvSnippet.setBackground(getResources().getDrawable(R.drawable.rounded_vacancy_green));
                    break;
                default: tvSnippet.setBackground(getResources().getDrawable(R.drawable.rounded_vacancy_blue));
                    break;
            }

            // Set snippet content to be just the relevant information
            tvSnippet.setText(lines[0]);

            return myContentsView;
        }

        @Override
        public View getInfoWindow(Marker marker) {
            return null;
        }
    }

    /**
     * Override back button to start a new MainActivity with the address bundled as an extra
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK))
        {
            Intent intent = new Intent(MapsActivity.this, MainActivity.class);
            intent.putExtra("address", userAddress);
            startActivity(intent);
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * onCreate method initializes activity variables
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.map);

        // Get intent extras
        Bundle extras = getIntent().getExtras();

        // Extract how many properties returned and initialize arrays accordingly
        totalProperties = extras.getInt("total");
        markers = new Marker[totalProperties];
        nearestProperties = new Property[totalProperties];

        // Extract user variables
        userLat = extras.getDouble("lat");
        userLng = extras.getDouble("lng");
        userAddress = extras.getString("address");

        // Extract property information and populate properties array
        for (int i = 0; i < totalProperties; i++) {
            nearestProperties[i] = new Property(extras.getString(i + "-name"),
                    extras.getString(i + "-id"),
                    extras.getDouble(i + "-lat"),
                    extras.getDouble(i + "-lng"),
                    extras.getDouble(i + "-avail"),
                    extras.getDouble(i + "-total"));
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
        mMap.setInfoWindowAdapter(new MyInfoWindowAdapter());

        // Info window click starts DetailActivity with property information bundled
        mMap.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
            @Override
            public void onInfoWindowClick(Marker marker) {
                Log.v("test", "button pressed");

                final String title = marker.getTitle();
                final String[] lines = marker.getSnippet().split("\n");

                Intent intent = new Intent(MapsActivity.this, DetailActivity.class);
                intent.putExtra("name", title);
                intent.putExtra("propertyId", lines[2]);
                intent.putExtra("total", lines[3]);
                intent.putExtra("avail", lines[4]);
                intent.putExtra("lat", userLat);
                intent.putExtra("lng", userLng);
                intent.putExtra("address", userAddress);

                startActivity(intent);
            }
        });

        // Create map marker for each property included in intent extras
        for (int i = 0; i < totalProperties; i++) {
            BitmapDescriptor markerColour;
            String vacancy;

            // Set vacancy based on how many couches are available out of total
            if ( nearestProperties[i].available / nearestProperties[i].total == 0 ) {
                vacancy = "FULL";
            } else if (nearestProperties[i].available / nearestProperties[i].total <= 0.5 ) {
                vacancy = "HALF FULL";
            } else if (nearestProperties[i].available / nearestProperties[i].total > 0.5 ) {
                vacancy = "OPEN";
            } else {
                vacancy = "ERROR";
            }

            // Set marker colour based on vacancy
            switch (vacancy) {
                case "FULL": markerColour = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);
                    break;
                case "HALF FULL": markerColour = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW);
                    break;
                case "OPEN": markerColour = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN);
                    break;
                default: markerColour = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE);
                    break;
            }

            // Build marker with information and add to map at property coordinates
            markers[i] = mMap.addMarker(new MarkerOptions()
                    .title(nearestProperties[i].name)
                    .snippet("Couches Available: " + (int)nearestProperties[i].available + "/" + (int)nearestProperties[i].total + "\n" +
                            vacancy + "\n" +
                            nearestProperties[i].id + "\n" +
                            nearestProperties[i].total + "\n" +
                            nearestProperties[i].available)
                    .position(new LatLng(nearestProperties[i].lat, nearestProperties[i].lng))
                    .icon(markerColour));
        }

        // Check if location service is enabled
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            // If disabled, set user location to be the user coordinates
            mMap.setMyLocationEnabled(false);
            mMap.addMarker(new MarkerOptions().icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_mylocation))
                    .position(new LatLng(userLat, userLng)));
        } else {
            // If enabled, set user location to the GPS change listener
            mMap.setMyLocationEnabled(true);
            mMap.setOnMyLocationChangeListener(myLocationChangeListener);
        }

        // Set default map view to center on the user coordinates
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(userLat, userLng), 12.5f));
    }

    /**
     * Location change listener updates the user coordinates
     */
    private GoogleMap.OnMyLocationChangeListener myLocationChangeListener = new GoogleMap.OnMyLocationChangeListener() {
        @Override
        public void onMyLocationChange(Location location) {
            LatLng loc = new LatLng(location.getLatitude(), location.getLongitude());

            userLat = loc.latitude;
            userLng = loc.longitude;
        }
    };

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
