package com.wurmonline.server.players;
import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.questions.Question;
public class Player extends com.wurmonline.server.creatures.Creature {
    public boolean linked = true;
    public boolean dead;
    public boolean undead;
    public Question current;
    public com.wurmonline.server.items.Item inventory;
    private Communicator comm;
    private final com.wurmonline.server.behaviours.ActionStack actions = new com.wurmonline.server.behaviours.ActionStack();
    public boolean isPlayer() { return true; }
    public com.wurmonline.server.behaviours.ActionStack getActions() { return actions; }
    public void setCommunicator(Communicator c) { comm = c; }
    public Communicator getCommunicator() { return comm; }
    public com.wurmonline.server.items.Item getInventory() { return inventory; }
    public boolean hasLink() { return linked; }
    public boolean isDead() { return dead; }
    public boolean isUndead() { return undead; }
}
