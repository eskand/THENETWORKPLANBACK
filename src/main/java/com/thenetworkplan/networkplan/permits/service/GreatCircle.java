package com.thenetworkplan.networkplan.permits.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Great-circle geometry, in one place.
 *
 * <p>Pure static arithmetic: no clock, no database, no Spring. Everything the
 * permit analysis knows about distance and about where an aircraft actually
 * flies comes from here, so there is exactly one definition of "the distance
 * between two aerodromes" in the product.
 */
public final class GreatCircle {

    /** Mean earth radius, in nautical miles. */
    private static final double EARTH_NM = 3440.065;

    private GreatCircle() {
    }

    public record Point(double latitude, double longitude) {
    }

    /** Distance in nautical miles, by the haversine formula. */
    public static double distanceNm(Point from, Point to) {
        double lat1 = Math.toRadians(from.latitude());
        double lat2 = Math.toRadians(to.latitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(to.longitude() - from.longitude());

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_NM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Points along the great circle from {@code from} to {@code to}, one every
     * {@code stepNm} nautical miles, both ends included.
     *
     * <p>Spherical interpolation, not linear: on a Paris–Dubai sector a straight
     * line in latitude and longitude wanders several hundred miles away from the
     * track an aircraft actually flies, and would attribute the distance to the
     * wrong countries.
     *
     * <p>The step is the accuracy of everything downstream. Ten nautical miles
     * puts the FIR boundary within five miles, which is finer than the charging
     * scheme's own rounding, and keeps a long-haul sector under six hundred
     * samples.
     */
    public static List<Point> sample(Point from, Point to, double stepNm) {
        double totalNm = distanceNm(from, to);
        int steps = Math.max(1, (int) Math.ceil(totalNm / Math.max(1, stepNm)));

        double lat1 = Math.toRadians(from.latitude());
        double lon1 = Math.toRadians(from.longitude());
        double lat2 = Math.toRadians(to.latitude());
        double lon2 = Math.toRadians(to.longitude());
        double angular = totalNm / EARTH_NM;

        List<Point> points = new ArrayList<>(steps + 1);
        if (angular == 0) {
            points.add(from);
            return points;
        }
        double sinAngular = Math.sin(angular);

        for (int i = 0; i <= steps; i++) {
            double fraction = (double) i / steps;
            double a = Math.sin((1 - fraction) * angular) / sinAngular;
            double b = Math.sin(fraction * angular) / sinAngular;

            double x = a * Math.cos(lat1) * Math.cos(lon1) + b * Math.cos(lat2) * Math.cos(lon2);
            double y = a * Math.cos(lat1) * Math.sin(lon1) + b * Math.cos(lat2) * Math.sin(lon2);
            double z = a * Math.sin(lat1) + b * Math.sin(lat2);

            points.add(new Point(
                    Math.toDegrees(Math.atan2(z, Math.sqrt(x * x + y * y))),
                    Math.toDegrees(Math.atan2(y, x))));
        }
        return points;
    }

    /**
     * Is the point inside the ring?
     *
     * <p>Ray casting: count how many edges a ray to the east crosses. An odd
     * count means inside. The ring is {@code [[lon, lat], …]}, the order the
     * annexe supplies, so longitude is index zero — getting that backwards puts
     * every aircraft in the wrong hemisphere, which is why it is stated here.
     */
    public static boolean insideRing(double latitude, double longitude, double[][] ring) {
        boolean inside = false;
        for (int i = 0, j = ring.length - 1; i < ring.length; j = i++) {
            double lonI = ring[i][0];
            double latI = ring[i][1];
            double lonJ = ring[j][0];
            double latJ = ring[j][1];

            boolean straddles = (latI > latitude) != (latJ > latitude);
            if (straddles
                    && longitude < (lonJ - lonI) * (latitude - latI) / (latJ - latI) + lonI) {
                inside = !inside;
            }
        }
        return inside;
    }
}
