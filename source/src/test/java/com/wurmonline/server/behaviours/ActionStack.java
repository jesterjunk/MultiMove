package com.wurmonline.server.behaviours;
import java.util.*;
import com.wurmonline.server.creatures.Creature;
public class ActionStack {
    private final LinkedList<Action> slow = new LinkedList<>();
    public int maxQueued;
    public int rejected;
    public void addAction(Action action) {
        // Deliberately strict to catch flooding or answering while busy.
        if (!slow.isEmpty()) { rejected++; return; }
        slow.addLast(action);
        maxQueued = Math.max(maxQueued, slow.size());
    }
    public Action getLastSlowAction() { return slow.isEmpty() ? null : slow.getLast(); }
    public boolean poll(Creature owner) {
        if (slow.isEmpty()) return true;
        if (slow.getFirst().poll()) { slow.removeFirst(); return true; }
        return false;
    }
    public void clear() {
        for (Action action : slow) action.stop(false);
        slow.clear();
    }
}
