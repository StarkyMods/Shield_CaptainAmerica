package co.carrd.starkymods.config;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class ShieldCapCraft {

    @SerializedName("Comment")
    public String comment = "If you wish, customize Cap Shield crafting recipe to your liking.";

    @SerializedName("Weapon Max Durability")
    public Integer weaponMaxDurability = null;

    public List<IngredientEntry> Input = new ArrayList<>();

    public int TimeSeconds = 4;
    public int RequiredMemoriesLevel = 2;

    @SerializedName("Bench Requirements")
    public List<BenchConfig> BenchRequirements = new ArrayList<>();

    public BenchConfig Bench = new BenchConfig();

    public static class IngredientEntry {
        public String ItemId;
        public int Quantity;

        public IngredientEntry() {
        }

        public IngredientEntry(String itemId, int quantity) {
            this.ItemId = itemId;
            this.Quantity = quantity;
        }
    }

    public static class BenchConfig {
        public String Id = "Armor_Bench";
        public int RequiredTierLevel = 3;
        public List<String> Categories = new ArrayList<>();
    }
}
