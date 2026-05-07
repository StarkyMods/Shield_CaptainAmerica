package co.carrd.starkymods.config;

import com.google.gson.annotations.SerializedName;

public class ShieldCapConfig {

    @SerializedName("Allow Shield Craft")
    public Boolean allowShieldCraft = true;

    @SerializedName("Shield keeps Signature Energy when swapped")
    public Boolean shieldKeepsSignatureEnergyWhenSwapped = true;

    @SerializedName("Visual Shield in player back")
    public Boolean visualShieldInPlayersBack = true;

    @SerializedName("Fall Resistance when blocking")
    public Boolean fallResistanceWhenBlocking = true;

    @SerializedName("Damage from the Back Resistance")
    public Boolean damageFromTheBackResistance = true;

    @SerializedName("Double Jump (disabling this includes the FallStar Attack)")
    public Boolean doubleJump = true;

    @SerializedName("FallStar Attack")
    public Boolean fallStarAttack = true;

    @SerializedName("Guard Bash")
    public Boolean guardBash = true;

    @SerializedName("Guard Bash Push Launch")
    public Boolean guardBashPushLaunch = true;

    @SerializedName("Sprint Attack")
    public Boolean sprintAttack = true;

    @SerializedName("Kick Push Attack")
    public Boolean kickPushAttack = true;

    @SerializedName("Throw")
    public Boolean throwAttack = true;

    @SerializedName("Throw Left Hand")
    public Boolean throwLeftHand = true;

    @SerializedName("Furious Onslaught")
    public Boolean furiousOnslaught = true;

    @SerializedName("Guard Bash Shockwave with Mjolnir")
    public Boolean guardBashShockwaveWithMjolnir = true;
}
