package com.wurmonline.client.renderer.gui;

import java.util.*;

/** Minimal fixture of Shift submission and per-row ordinary ID clipping. */
public class InventoryListComponent {
    public long[][] groups = new long[0][];
    public int nativeCalls;
    public long[] nativeIds;
    public long[] getSelectedCommandTargets(int amount) {
        List<Long> ids = new ArrayList<>();
        for (long[] group : groups) {
            int length = amount > 0 ? Math.min(amount, group.length) : group.length;
            for (int i = 0; i < length; i++) ids.add(group[i]);
        }
        long[] result = new long[ids.size()];
        for (int i = 0; i < result.length; i++) result[i] = ids.get(i);
        return result;
    }
    public void plainDrag() { nativeCalls++; nativeIds = getSelectedCommandTargets(0); }
    public void chooseAmount(InventoryTreeListItem row, long destination, int amount) { itemDroppedAmount(row, destination, amount); }
    void itemDroppedAmount(InventoryTreeListItem row, long destination, int amount) {
        nativeCalls++; nativeIds = row.getOwner().getSelectedCommandTargets(amount);
    }
    public static abstract class InventoryTreeListItem {
        private final InventoryListComponent owner;
        public InventoryTreeListItem(InventoryListComponent owner) { this.owner = owner; }
        final InventoryListComponent getOwner() { return owner; }
    }
    public static final class SingleTreeListItem extends InventoryTreeListItem {
        public SingleTreeListItem(InventoryListComponent owner) { super(owner); }
    }
}
