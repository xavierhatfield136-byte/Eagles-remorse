import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignZoneLayoutTest {

    @Test
    void campaignZonesKeepAtLeastFiveThousandUnitsBetweenNeighbors() throws Exception {
        Method centerX = CampaignSystem.class.getDeclaredMethod("getZoneCenterX", int.class);
        Method centerY = CampaignSystem.class.getDeclaredMethod("getZoneCenterY", int.class);
        centerX.setAccessible(true);
        centerY.setAccessible(true);

        double sector1X = (double) centerX.invoke(null, 1);
        double sector1Y = (double) centerY.invoke(null, 1);
        double sector2X = (double) centerX.invoke(null, 2);
        double sector2Y = (double) centerY.invoke(null, 2);
        double sector9X = (double) centerX.invoke(null, 9);
        double sector9Y = (double) centerY.invoke(null, 9);

        assertEquals(9000.0, sector2X - sector1X, 0.001);
        assertEquals(0.0, sector2Y - sector1Y, 0.001);
        assertEquals(0.0, sector9X - sector1X, 0.001);
        assertEquals(8000.0, sector9Y - sector1Y, 0.001);
        assertTrue(sector2X - sector1X >= 9000.0);
        assertTrue(sector9Y - sector1Y >= 8000.0);
    }
}
