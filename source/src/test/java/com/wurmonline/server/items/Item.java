package com.wurmonline.server.items;
import com.wurmonline.server.Items;
public class Item {
    public final long id;
    public long parent = -10;
    public boolean hollow;
    public boolean bulkContainer;
    public boolean bulk;
    public boolean rejected;
    public boolean fail;
    public boolean inventory;
    public int template = 1;
    public int capacity = Integer.MAX_VALUE;
    public int contained;
    public int units = 5;
    public int transferLimit = Integer.MAX_VALUE;
    public Item(long id) { this.id = id; Items.items.put(id, this); }
    public long getWurmId() { return id; }
    public long getParentId() { return parent; }
    public Item getParentOrNull() { return Items.items.get(parent); }
    public boolean isHollow() { return hollow; }
    public boolean isBulkContainer() { return bulkContainer; }
    public int getTemplateId() { return template; }
    public int getNumItemsNotCoins() { return contained; }
}
