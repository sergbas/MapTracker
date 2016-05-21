package demo.apps.maptracker.common;

import android.location.Location;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;

public class Utils {

    public static double calculationByDistance(LatLng startP, LatLng endP) {
        float[] results = new float[1];
        Location.distanceBetween(startP.latitude, startP.longitude, endP.latitude, endP.longitude, results);

        return results[0];
    }

    public static List<LatLng> decodePoly(String encoded) {
        List<LatLng> poly = new ArrayList<LatLng>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)),
                    (((double) lng / 1E5)));

            poly.add(p);
        }

        return poly;
    }

    public static String makeURL(double sourcelat, double sourcelog, double destlat, double destlog) {
        StringBuilder urlString = new StringBuilder();
        urlString.append("https://maps.googleapis.com/maps/api/directions/json");
        urlString.append("?origin=");// from
        urlString.append(Double.toString(sourcelat));
        urlString.append(",");
        urlString
                .append(Double.toString(sourcelog));
        urlString.append("&destination=");// to
        urlString
                .append(Double.toString(destlat));
        urlString.append(",");
        urlString.append(Double.toString(destlog));
        urlString.append("&sensor=false&mode=walking&alternatives=true");
        urlString.append("&key=AIzaSyA_QY5Hyxg2hif-W-IDpupTUw0yrxjAeC4");
        return urlString.toString();
    }

    public static int findNearestSegment(List<Segment> edges, LatLng loc){
        int indx = 0;
        double dmin = Double.MAX_VALUE;
        for(int i = 0; i < edges.size(); i++ )
        {
            Segment e = edges.get(i);

            LatLng closestPoint = getClosestPointOnLine(e.start, e.finish, loc);
            double dx = loc.latitude - closestPoint.latitude;
            double dy = loc.longitude - closestPoint.longitude;
            double d = dx*dx + dy*dy;

            if(d < dmin) {
                dmin = d; indx = i;
            }
        }

        Segment s = edges.get(indx);

        float[] dist = new float[1];
        Location.distanceBetween(loc.latitude, loc.longitude, s.finish.latitude, s.finish.longitude, dist);

        return indx;
    }

    private static LatLng getClosestPointOnLine(LatLng start, LatLng end, LatLng p)
    {
        double dx = end.latitude - start.latitude;
        double dy = end.longitude - start.longitude;
        double length = dx*dx + dy*dy;
        if (length == 0.0)
        {
            return start;
        }
        LatLng v = new LatLng(dx, dy);
        LatLng ps = new LatLng(p.latitude - start.latitude, p.longitude - start.longitude);
        double psv = ps.latitude * v.latitude + ps.longitude * v.longitude;
        double param = psv / length;
        return (param < 0.0) ? start : (param > 1.0) ? end : new LatLng(start.latitude + param * v.latitude, start.longitude + param * v.longitude);
    }

    public static double getDistanceToFinishFrom(List<Segment> edges, int from) {
        double x = 0;
        for(int i=from+1; i<edges.size(); i++) {
            x += edges.get(i).LengthInMeters;
        }
        return x;
    }
}
