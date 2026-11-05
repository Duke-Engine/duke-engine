package uz.dukeengine.render;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;

/**
 * The sky an observer on the ground sees under a sun in {@code toSun}: light the air scatters toward the eye once,
 * Rayleigh (small molecules, so blue) and Mie (haze, forward and white), with ozone taking the rest of the red out of
 * a low sun's path. Colour comes from the sun's direction alone, so noon is blue and sunset orange with no table of
 * hours. Constants are Hillaire's, 2020, "A Scalable and Production Ready Sky and Atmosphere Rendering Technique".
 *
 * <p>The sun's colour is the light that reaches the top of the air, in the engine's units: what a white matte surface
 * facing the sun would take is that colour, so the sky's brightness follows from it and no ambient is asked for.
 *
 * <p>shortcut: one scattering and no ground bounce, so shadows are darker than under a real sky; what is under the
 * horizon is half the horizon's light, and none of the sun's own. Add a multiple scattering term and a lit ground when
 * a game wants brighter shade.
 */
public final class Atmosphere implements SkyProbe.Sky {

    private static final double EARTH = 6_360_000.0;
    private static final double TOP = 6_420_000.0;
    private static final double EYE = EARTH + 2.0;
    private static final double RAYLEIGH_HEIGHT = 8_000.0;
    private static final double MIE_HEIGHT = 1_200.0;
    private static final double[] RAYLEIGH = {5.802e-6, 13.558e-6, 33.1e-6};
    private static final double MIE_SCATTER = 3.996e-6;
    private static final double MIE_EXTINCTION = 4.44e-6;
    private static final double[] OZONE = {0.650e-6, 1.881e-6, 0.085e-6};
    private static final double MIE_G = 0.8;
    private static final int VIEW_STEPS = 12;
    private static final int LIGHT_STEPS = 4;
    private static final int TRANSMITTANCE_STEPS = 24;
    /** What the ground gives back of the horizon's light: half of it, since nothing lights it from below. */
    private static final float GROUND = 0.5f;

    private final double sx;
    private final double sy;
    private final double sz;
    private final double[] sun;
    private final ColorRGBA underFoot;

    /** The sky under a sun in {@code toSun}, of the engine colour {@code sun}. */
    public Atmosphere(Vector3f toSun, ColorRGBA sun) {
        var s = toSun.normalize();
        this.sx = s.x;
        this.sy = s.y;
        this.sz = s.z;
        // Engine colour is radiance a unit surface returns, which is irradiance over pi.
        this.sun = new double[] {sun.r * Math.PI, sun.g * Math.PI, sun.b * Math.PI};
        double[] mean = new double[3];
        for (var around : new double[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            var l = radiance(around[0], 0, around[1]);
            for (int c = 0; c < 3; c++) {
                mean[c] += l[c] * GROUND / 4.0;
            }
        }
        this.underFoot = new ColorRGBA((float) mean[0], (float) mean[1], (float) mean[2], 1f);
    }

    @Override
    public ColorRGBA at(Vector3f d) {
        var n = d.normalize();
        if (n.y < 0f) {
            // Half the horizon's light that way; straight down there is no way, so the mean of the four.
            float around = (float) Math.sqrt(n.x * n.x + n.z * n.z);
            if (around < 1e-4f) {
                return underFoot.clone();
            }
            var l = radiance(n.x / around, 0, n.z / around);
            float own = Math.min(1f, around * 3f);
            return new ColorRGBA((float) l[0] * GROUND * own + underFoot.r * (1f - own),
                    (float) l[1] * GROUND * own + underFoot.g * (1f - own),
                    (float) l[2] * GROUND * own + underFoot.b * (1f - own), 1f);
        }
        var l = radiance(n.x, n.y, n.z);
        return new ColorRGBA((float) l[0], (float) l[1], (float) l[2], 1f);
    }

    /**
     * What is left of the sun's colour by the time it reaches the ground: 1 for none lost, near 0 in the red and well
     * under it in the blue for a sun on the horizon, 0 for a sun under it.
     */
    public static ColorRGBA transmittance(Vector3f toSun) {
        var s = toSun.normalize();
        double b = EYE * s.y;
        if (b < 0 && b * b - (EYE * EYE - EARTH * EARTH) > 0) {
            return new ColorRGBA(0f, 0f, 0f, 1f);
        }
        double length = toTop(0, EYE, 0, s.x, s.y, s.z);
        double dt = length / TRANSMITTANCE_STEPS;
        double rayleigh = 0;
        double mie = 0;
        double ozone = 0;
        for (int i = 0; i < TRANSMITTANCE_STEPS; i++) {
            double t = (i + 0.5) * dt;
            double h = Math.sqrt(Math.pow(s.x * t, 2) + Math.pow(EYE + s.y * t, 2) + Math.pow(s.z * t, 2)) - EARTH;
            rayleigh += Math.exp(-h / RAYLEIGH_HEIGHT) * dt;
            mie += Math.exp(-h / MIE_HEIGHT) * dt;
            ozone += ozone(h) * dt;
        }
        return new ColorRGBA(out(0, rayleigh, mie, ozone), out(1, rayleigh, mie, ozone), out(2, rayleigh, mie, ozone), 1f);
    }

    private static float out(int channel, double rayleigh, double mie, double ozone) {
        return (float) Math.exp(-(RAYLEIGH[channel] * rayleigh + MIE_EXTINCTION * mie + OZONE[channel] * ozone));
    }

    private static double ozone(double h) {
        return Math.max(0.0, 1.0 - Math.abs(h - 25_000.0) / 15_000.0);
    }

    /** Distance from {@code (x, y, z)}, inside the air, along a unit direction to the top of it. */
    private static double toTop(double x, double y, double z, double dx, double dy, double dz) {
        double b = x * dx + y * dy + z * dz;
        double c = x * x + y * y + z * z - TOP * TOP;
        return -b + Math.sqrt(b * b - c);
    }

    /** The light along a direction at or above the horizon, summed over the view ray's samples. */
    private double[] radiance(double dx, double dy, double dz) {
        double length = toTop(0, EYE, 0, dx, dy, dz);
        double cos = dx * sx + dy * sy + dz * sz;
        double rayleighPhase = 3.0 / (16.0 * Math.PI) * (1.0 + cos * cos);
        double g2 = MIE_G * MIE_G;
        double miePhase = (1.0 - g2) / (4.0 * Math.PI * Math.pow(1.0 + g2 - 2.0 * MIE_G * cos, 1.5));
        double[] rayleighSum = new double[3];
        double[] mieSum = new double[3];
        double viewRayleigh = 0;
        double viewMie = 0;
        double viewOzone = 0;
        double previous = 0;
        for (int i = 0; i < VIEW_STEPS; i++) {
            // Quadratic spacing: thin steps where the air is thick, which is near the eye.
            double u = (i + 1.0) / VIEW_STEPS;
            double t = length * u * u;
            double mid = 0.5 * (previous + t);
            double dt = t - previous;
            previous = t;
            double px = dx * mid;
            double py = EYE + dy * mid;
            double pz = dz * mid;
            double h = Math.sqrt(px * px + py * py + pz * pz) - EARTH;
            double densityRayleigh = Math.exp(-h / RAYLEIGH_HEIGHT);
            double densityMie = Math.exp(-h / MIE_HEIGHT);
            viewRayleigh += densityRayleigh * dt;
            viewMie += densityMie * dt;
            viewOzone += ozone(h) * dt;

            double b = px * sx + py * sy + pz * sz;
            double r2 = px * px + py * py + pz * pz;
            if (b < 0 && b * b - (r2 - EARTH * EARTH) > 0) {
                continue; // the planet is between this air and the sun
            }
            double lightLength = -b + Math.sqrt(b * b - (r2 - TOP * TOP));
            double ds = lightLength / LIGHT_STEPS;
            double lightRayleigh = 0;
            double lightMie = 0;
            double lightOzone = 0;
            for (int k = 0; k < LIGHT_STEPS; k++) {
                double lt = (k + 0.5) * ds;
                double lx = px + sx * lt;
                double ly = py + sy * lt;
                double lz = pz + sz * lt;
                double lh = Math.sqrt(lx * lx + ly * ly + lz * lz) - EARTH;
                lightRayleigh += Math.exp(-lh / RAYLEIGH_HEIGHT) * ds;
                lightMie += Math.exp(-lh / MIE_HEIGHT) * ds;
                lightOzone += ozone(lh) * ds;
            }
            for (int c = 0; c < 3; c++) {
                double tau = RAYLEIGH[c] * (viewRayleigh + lightRayleigh) + MIE_EXTINCTION * (viewMie + lightMie)
                        + OZONE[c] * (viewOzone + lightOzone);
                double attenuation = Math.exp(-tau);
                rayleighSum[c] += attenuation * densityRayleigh * dt;
                mieSum[c] += attenuation * densityMie * dt;
            }
        }
        var light = new double[3];
        for (int c = 0; c < 3; c++) {
            light[c] = sun[c] * (RAYLEIGH[c] * rayleighPhase * rayleighSum[c] + MIE_SCATTER * miePhase * mieSum[c]);
        }
        return light;
    }
}
