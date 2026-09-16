package com.thenetworkplan.networkplan.permits;

import static org.assertj.core.api.Assertions.assertThat;

import com.thenetworkplan.networkplan.permits.service.GreatCircle;
import com.thenetworkplan.networkplan.permits.service.GreatCircle.Point;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The geometry every permit and every charge rests on.
 *
 * <p>Distances are checked against published great-circle figures. A one
 * percent tolerance is generous for a spherical earth model; the point of the
 * test is to catch a swapped latitude and longitude or a degrees-versus-radians
 * slip, both of which move an aircraft to another continent rather than another
 * mile.
 */
class GreatCircleTest {

    private static final Point LFPG = new Point(49.0097, 2.5479);   // Paris CDG
    private static final Point OMDB = new Point(25.2528, 55.3644);  // Dubai
    private static final Point DTTA = new Point(36.8510, 10.2272);  // Tunis
    private static final Point LFML = new Point(43.4393, 5.2214);   // Marseille

    @Test
    @DisplayName("Paris to Dubai is 2 829 NM — 5 238 km, the published figure")
    void parisDubai() {
        assertThat(GreatCircle.distanceNm(LFPG, OMDB)).isCloseTo(2829, org.assertj.core.data.Offset.offset(15.0));
    }

    @Test
    @DisplayName("Tunis to Marseille is 457 NM — 846 km")
    void tunisMarseille() {
        assertThat(GreatCircle.distanceNm(DTTA, LFML)).isCloseTo(457, org.assertj.core.data.Offset.offset(6.0));
    }

    @Test
    @DisplayName("the same point is zero away from itself")
    void samePoint() {
        assertThat(GreatCircle.distanceNm(DTTA, DTTA)).isZero();
    }

    @Test
    @DisplayName("distance does not depend on the direction of travel")
    void symmetric() {
        assertThat(GreatCircle.distanceNm(LFPG, OMDB))
                .isCloseTo(GreatCircle.distanceNm(OMDB, LFPG), org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("the sample starts at the origin, ends at the destination, and follows the arc")
    void sampleEndpoints() {
        List<Point> path = GreatCircle.sample(LFPG, OMDB, 10);

        assertThat(path).hasSizeGreaterThan(200);
        assertThat(path.getFirst().latitude()).isCloseTo(LFPG.latitude(), org.assertj.core.data.Offset.offset(0.01));
        assertThat(path.getLast().longitude()).isCloseTo(OMDB.longitude(), org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("the arc bends north of the straight line — that is the whole point")
    void sampleIsNotLinear() {
        List<Point> path = GreatCircle.sample(LFPG, OMDB, 10);
        Point middle = path.get(path.size() / 2);

        // A linear interpolation would put the midpoint at latitude 37.1.
        // The great circle passes clearly north of it.
        double linearMidLatitude = (LFPG.latitude() + OMDB.latitude()) / 2;
        assertThat(middle.latitude()).isGreaterThan(linearMidLatitude + 0.5);
    }

    @Test
    @DisplayName("a point inside a square reads as inside, one outside reads as outside")
    void ringMembership() {
        // [lon, lat] — the order the annexe supplies.
        double[][] square = { { 0, 0 }, { 10, 0 }, { 10, 10 }, { 0, 10 }, { 0, 0 } };

        assertThat(GreatCircle.insideRing(5, 5, square)).isTrue();
        assertThat(GreatCircle.insideRing(15, 5, square)).isFalse();
        assertThat(GreatCircle.insideRing(5, 15, square)).isFalse();
        assertThat(GreatCircle.insideRing(-1, 5, square)).isFalse();
    }

    @Test
    @DisplayName("a concave ring does not swallow the notch")
    void concaveRing() {
        // An L shape: the notch at (8, 8) is outside even though it is inside
        // the bounding box. This is the case a bounding-box-only test gets wrong.
        double[][] shape = { { 0, 0 }, { 10, 0 }, { 10, 5 }, { 5, 5 }, { 5, 10 }, { 0, 10 }, { 0, 0 } };

        assertThat(GreatCircle.insideRing(2, 2, shape)).isTrue();
        assertThat(GreatCircle.insideRing(8, 8, shape)).isFalse();
    }
}
