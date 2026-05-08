package co.carrd.starkymods.util;

import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import java.util.ArrayList;
import java.util.List;

public final class ShieldCapInventoryCompat {
    private ShieldCapInventoryCompat() {
    }

    public static ItemContainer getCombinedEverything(Inventory inventory) {
        if (inventory == null) {
            return null;
        }

        List<ItemContainer> containers = new ArrayList<>(6);
        addIfPresent(containers, inventory.getHotbar());
        addIfPresent(containers, inventory.getStorage());
        addIfPresent(containers, inventory.getBackpack());
        addIfPresent(containers, inventory.getUtility());
        addIfPresent(containers, inventory.getTools());
        addIfPresent(containers, inventory.getArmor());

        if (containers.isEmpty()) {
            return null;
        }
        if (containers.size() == 1) {
            return containers.get(0);
        }

        return new CombinedItemContainer(containers.toArray(ItemContainer[]::new));
    }

    private static void addIfPresent(List<ItemContainer> containers, ItemContainer container) {
        if (container != null) {
            containers.add(container);
        }
    }
}
