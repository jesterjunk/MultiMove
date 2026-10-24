package com.wurmonline.server;
import java.util.*;
import com.wurmonline.server.items.Item;
public final class Items {
    public static final Map<Long, Item> items = new LinkedHashMap<>();
    public static Item getItem(long id) throws NoSuchItemException {
        Item i = items.get(id);
        if (i == null) throw new NoSuchItemException(id);
        return i;
    }
}
