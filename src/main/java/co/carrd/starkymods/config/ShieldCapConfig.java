package co.carrd.starkymods.config;

import com.google.gson.annotations.SerializedName;

public class ShieldCapConfig {

    @SerializedName("Allow Shield Craft")
    public Boolean allowShieldCraft = true;

    @SerializedName("Shield keeps Signature Energy when swapped")
    public Boolean shieldKeepsSignatureEnergyWhenSwapped = true;

    @SerializedName("Visual Shield in player back")
    public Boolean visualShieldInPlayersBack = true;
}
