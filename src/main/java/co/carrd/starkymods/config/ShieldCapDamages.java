package co.carrd.starkymods.config;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShieldCapDamages {
    public String comment = "Customize Cap Shield damage and knockback values here. Save the file to live reload in-game.";
    public Integer schemaVersion = 2;

    @SerializedName("DAMAGE VALUES")
    public Map<String, Double> damageValues = new LinkedHashMap<>();

    @SerializedName("KNOCKBACK AND LAUNCH FORCES")
    public Map<String, Double> launchForces = new LinkedHashMap<>();

    @SerializedName("Mod Compatibility")
    public Boolean modCompatibility = true;

    @SerializedName("Note")
    public List<String> modCompatibilityNote = new ArrayList<>();

    @SerializedName("Disable Compatibility Profiles")
    public Boolean legacyDisableCompatibilityProfiles;

    public double getDamage(String key) {
        return getValue(damageValues, key);
    }

    public double getLaunchForce(String key) {
        return getValue(launchForces, key);
    }

    private double getValue(Map<String, Double> values, String key) {
        if (values == null || key == null) {
            return 0.0;
        }
        Double value = values.get(key);
        if (value == null || value.isNaN() || value.isInfinite()) {
            return 0.0;
        }
        return Math.max(0.0, value);
    }
}
