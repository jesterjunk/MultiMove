package com.wurmonline.server.behaviours;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.creatures.Creature;
public class Action {
    private final Player player;
    private final Runnable completion;
    private int remaining = 1;
    public boolean stopped;
    public Action(Player p, Runnable onDone) { player = p; completion = onDone; }
    public Creature getPerformer() { return player; }
    public boolean poll() {
        if (remaining-- > 0) return false;
        if (!stopped && completion != null) completion.run();
        return true;
    }
    public String stop(boolean farAway) { stopped = true; return "You stop."; }
}
