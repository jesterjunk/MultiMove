package com.wurmonline.server.questions;
import java.util.*;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.items.Item;
public class RemoveItemQuestion extends Question {
    public static final java.util.List<Integer> answersReceived = new java.util.ArrayList<>();
    public static int answerDepth;
    public static int maximumDepth;
    private final Item item;
    private final Item destination;
    public RemoveItemQuestion(Player p, Item source, Item target) {
        super(p);
        item = source;
        destination = target;
    }
    public void sendQuestion() { responder.getCommunicator().dialogs.add(this); }
    public void answer(Properties answers) {
        String n = answers.getProperty("numstext");
        if (n == null || n.isEmpty()) n = answers.getProperty("items");
        try {
            final int count = Integer.parseInt(n);
            answersReceived.add(count);
            answerDepth++;
            maximumDepth = Math.max(maximumDepth, answerDepth);
            if (count > 0 && responder.hasLink() && !responder.isDead() && !responder.isUndead()
                    && !item.rejected && destination.contained < destination.capacity) {
                Runnable transfer = new Runnable() {
                    public void run() {
                        if (!responder.hasLink() || responder.isDead() || responder.isUndead()
                                || item.rejected || destination.contained >= destination.capacity) return;
                        int moved = Math.min(Math.min(count, item.units), destination.transferLimit);
                        // Model the native maximum withdrawal's live slot calculation.
                        if (destination.inventory) moved = Math.min(moved,
                                Math.max(0, destination.capacity - destination.getNumItemsNotCoins()));
                        if (moved <= 0) return;
                        item.units -= moved;
                        responder.getCommunicator().moves.add(item.id);
                        destination.contained += destination.inventory ? moved : 1;
                    }
                };
                if (destination.bulkContainer) {
                    responder.getActions().addAction(new com.wurmonline.server.behaviours.Action(responder, transfer));
                } else transfer.run();
            }
        } catch (RuntimeException invalid) { }
        finally { if (n != null) answerDepth = Math.max(0, answerDepth - 1); }
    }
}
