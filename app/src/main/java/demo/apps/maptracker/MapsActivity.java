package demo.apps.maptracker;

import android.content.Context;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;

import android.location.LocationListener;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import demo.apps.maptracker.common.Segment;
import demo.apps.maptracker.common.Utils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = "MapsActivity";
    private static final boolean DEBUG = true;
    private GoogleMap mMap;
    private LocationRequest lr;
    private double latitude;
    private double longitude;
    private Geocoder geocoder;
    private List<android.location.Address> addresses;

    private List<LatLng> myWay = new ArrayList<>();
    private List<Segment> edges = new ArrayList<>();

    private TextView tvLoc;
    private int nearest = 0;

    OkHttpClient client = new OkHttpClient();
    private Button btn;
    private List<LatLng> points;

    String doGetRequest(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        Response response = client.newCall(request).execute();
        return response.body().string();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        tvLoc = (TextView) findViewById(R.id.tvLocation);
        btn = (Button) findViewById(R.id.btnChangePath);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                (new AsyncPathBuilder()).execute(new LatLng(latitude, longitude));
            }
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        LocationListener ll = new MyLocationListener();
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 3, ll);
        Location loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        latitude = loc.getLatitude();
        longitude = loc.getLongitude();
        Log.i(TAG, String.format("Last location: %s, %s", latitude, longitude));

        lr = LocationRequest.create();
        lr.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

    }

    private class AsyncPathBuilder extends AsyncTask<LatLng, Void, String> {
        private String json = "";

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            drawPath(json);
            updatePath(new LatLng(latitude, longitude));
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(LatLng... params) {
            try {
                Random r = new Random();
                json = doGetRequest(Utils.makeURL(
                        params[0].latitude,
                        params[0].longitude,
                        params[0].latitude + r.nextDouble() * 0.01 - 0.005,
                        params[0].longitude + r.nextDouble() * 0.01 - 0.005));
            } catch (IOException e) {
                e.printStackTrace();
            }

            return json;
        }
    }

    public void drawPath(String result) {
        try {
            final JSONObject json = new JSONObject(result);
            JSONArray routeArray = json.getJSONArray("routes");
            JSONObject routes = routeArray.getJSONObject(0);
            JSONObject overviewPolylines = routes.getJSONObject("overview_polyline");
            String encodedString = overviewPolylines.getString("points");

            points = Utils.decodePoly(encodedString);

            double pathDist = 0;
            edges.clear();

            for (int z = 0; z < points.size()-1; z++) {
                LatLng src = points.get(z);
                LatLng dest = points.get(z + 1);

                double len = Utils.calculationByDistance(src, dest);

                edges.add(new Segment(src, dest, len));
                //pathDist += len;
            }

            drawEdges();

        } catch (JSONException e) {

        }
    }

    private void drawEdges() {
        if(mMap == null) return;

        mMap.clear();

        if(points != null)
            mMap.addPolyline(new PolylineOptions()
                    .addAll(points)
                    .width(12)
                    .color(Color.parseColor("#05b1fc"))
                    .geodesic(true)
            );

        for (int z = 0; z < edges.size(); z++) {
            LatLng src = edges.get(z).start;
            LatLng dest = edges.get(z).finish;

            if (z == nearest)
                mMap.addPolyline(new PolylineOptions()
                        .add(new LatLng(src.latitude, src.longitude), new LatLng(dest.latitude, dest.longitude))
                        .width(12)
                        .color(Color.YELLOW).geodesic(true));

            float r = 5;
            if (z == edges.size()-1) r = 25;

            CircleOptions co = new CircleOptions();
            co.center(dest);
            co.radius(r);
            co.fillColor(Color.YELLOW);
            co.strokeColor(Color.MAGENTA);
            mMap.addCircle(co);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.i(TAG, "onMapReady");

        mMap = googleMap;
        mMap.setMyLocationEnabled(true);

        if(DEBUG)
            mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                                           @Override
                                           public void onMapClick(LatLng arg0) {
                                               Log.d("arg0", arg0.latitude + "-" + arg0.longitude);
                                               latitude = arg0.latitude;
                                               longitude = arg0.longitude;

                                               moveTo(latitude, longitude);
                                           }
                                       });
        updateLocation();
    }

    public class MyLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(Location arg0) {
            Log.i(TAG, "onLocationChanged");

            latitude = arg0.getLatitude();
            longitude = arg0.getLongitude();

            moveTo(latitude, longitude);
        }

        @Override
        public void onProviderDisabled(String arg0) {
        }

        @Override
        public void onProviderEnabled(String arg0) {
        }

        @Override
        public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
        }
    }

    private void moveTo(double latitude, double longitude) {
        drawEdges();
        updatePath(new LatLng(latitude, longitude));
        updateLocation();
    }

    private void updatePath(LatLng loc) {
        if(edges.size()>0) {
            nearest = Utils.findNearestSegment(edges, loc);
            Log.i(TAG, "FindNearestSegment:" + nearest);

            int distTo = nearest + 1;
            if (distTo > edges.size() - 1) distTo = edges.size() - 1;

            double lenEdge = edges.get(nearest).LengthInMeters;
            double distToNext = Utils.calculationByDistance(new LatLng(latitude, longitude), edges.get(nearest).finish);
            double df = Utils.getDistanceToFinishFrom(edges, nearest);
            double metersLeft = df + distToNext;

            tvLoc.setText(String.format("до финиша: %.0f м", metersLeft));
        }

        drawEdges();
    }

    private void updateLocation() {
        String city = "Anywhere";
        try {
            geocoder = new Geocoder(MapsActivity.this, Locale.ENGLISH);
            addresses = geocoder.getFromLocation(latitude, longitude, 1);

            if (geocoder.isPresent()) {
                Address returnAddress = addresses.get(0);
                city = returnAddress.getLocality();
                Log.i(TAG, "city:" + city);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mMap != null) {
            LatLng loc = new LatLng(latitude, longitude);

            myWay.add(loc);

            CircleOptions co = new CircleOptions();
            co.center(loc);
            co.radius(9);
            co.fillColor(Color.RED);
            co.strokeColor(Color.RED);

            Circle c = mMap.addCircle(co);

            CameraUpdate cam = CameraUpdateFactory.newLatLngZoom(loc, 15);
            CameraUpdate zoom = CameraUpdateFactory.zoomTo(15);
            mMap.moveCamera(CameraUpdateFactory.newLatLng(loc));
            mMap.animateCamera(zoom);
        }
    }
}