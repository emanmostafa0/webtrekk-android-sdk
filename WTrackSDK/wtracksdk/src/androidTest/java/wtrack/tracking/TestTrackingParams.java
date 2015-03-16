package wtrack.tracking;

import android.test.AndroidTestCase;
import static wtrack.tracking.TrackingParams.Params;

/**
 * contains unittest functions to test that the TrackingParams class is working as expected
 */
public class TestTrackingParams extends AndroidTestCase {

    public void test_createTrackingParams() {
        TrackingParams tp = new TrackingParams();
        tp.add(Params.DEVICE,"Google Nexus 4")
                .add(Params.ACTION_NAME,"button save clicked")
                .add(Params.GNAME, "Thomas");
        assertEquals(tp.getTparams().size(), 3);
        assertTrue(tp.getTparams().get(Params.GNAME).equals("Thomas"));
    }

}
