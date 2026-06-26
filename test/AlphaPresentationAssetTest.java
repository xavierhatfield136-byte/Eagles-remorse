import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.File;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlphaPresentationAssetTest {

    @Test
    void temporaryFleetHubCrewChatterIsDisabledForAlpha() throws Exception {
        Field field = AudioSystem.class.getDeclaredField("ALPHA_TEMPORARY_CREW_CHATTER_ENABLED");
        field.setAccessible(true);

        assertFalse(field.getBoolean(null),
                "temporary fleet-hub banter/ambient voice should stay muted until replacement VO is approved");
    }

    @Test
    void shipDeathMajorSoundIsNoLongerTheShortTonePlaceholder() throws Exception {
        SfxManifest.EventSpec spec = SfxManifest.byId("impact.ship_death_major");
        assertTrue(spec != null);
        assertTrue(SfxManifest.variantCount(spec) >= spec.requiredVariants());

        File wav = new File("assets/audio/impacts/ship_death_major_01.wav");
        assertTrue(wav.isFile());
        assertTrue(wav.length() >= 70_000,
                "ship death sound should be a longer layered detonation asset, not the old short tone");
        assertTrue(peakAmplitude(wav) < 0.995,
                "replacement ship death sound should not be clipped");
    }

    @Test
    void blueSuperweaponChargeAssetsMatchGameplayChargeTime() throws Exception {
        File superCharge = new File("assets/audio/weapons/super_blue_charge_01.wav");
        File hyperCharge = new File("assets/audio/weapons/hyper_blue_charge_01.wav");
        assertTrue(superCharge.isFile());
        assertTrue(hyperCharge.isFile());

        assertEquals(Ship.SUPERWEAPON_CHARGE_SFX_SECONDS, durationSeconds(superCharge), 0.02);
        assertEquals(Ship.SUPERWEAPON_CHARGE_SFX_SECONDS, durationSeconds(hyperCharge), 0.02);
    }

    private static double durationSeconds(File wav) throws Exception {
        try (AudioInputStream in = javax.sound.sampled.AudioSystem.getAudioInputStream(wav)) {
            AudioFormat fmt = in.getFormat();
            return in.getFrameLength() / fmt.getFrameRate();
        }
    }

    private static double peakAmplitude(File wav) throws Exception {
        try (AudioInputStream in = javax.sound.sampled.AudioSystem.getAudioInputStream(wav)) {
            AudioFormat fmt = in.getFormat();
            byte[] buf = in.readAllBytes();
            boolean big = fmt.isBigEndian();
            double peak = 0.0;
            for (int i = 0; i + 1 < buf.length; i += 2) {
                int lo = buf[i] & 0xFF;
                int hi = buf[i + 1];
                short sample = big ? (short) ((lo << 8) | (hi & 0xFF)) : (short) ((hi << 8) | lo);
                peak = Math.max(peak, Math.abs(sample / (double) Short.MAX_VALUE));
            }
            return peak;
        }
    }
}
