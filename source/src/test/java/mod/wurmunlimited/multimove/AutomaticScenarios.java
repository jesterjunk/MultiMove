package mod.wurmunlimited.multimove;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.Logger;
import com.wurmonline.server.Items;
import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.questions.*;

public final class AutomaticScenarios {
    private static int checks;
    private static Player player;
    private static Communicator comm;
    private static Item target;
    private static long id(int n) { return n * 256L + 2L; }
    private static void check(boolean ok, String text) {
        checks++;
        if (!ok) throw new AssertionError(text);
    }
    private static void reset(boolean timed) {
        Items.items.clear(); Questions.active.clear();
        RemoveItemQuestion.answersReceived.clear();
        RemoveItemQuestion.answerDepth = 0; RemoveItemQuestion.maximumDepth = 0;
        player = new Player(); comm = new Communicator(player);
        target = new Item(id(2000)); target.hollow = true; target.bulkContainer = timed;
    }
    private static Item item(int n, boolean bulk) { Item i = new Item(id(n)); i.bulk = bulk; return i; }
    private static void drag(Item... items) throws Exception {
        dragTo(target.id, items);
    }
    private static void dragTo(long destination, Item... items) throws Exception {
        ByteBuffer packet = ByteBuffer.allocate(10 + 8 * items.length);
        packet.putShort((short)items.length);
        for (Item i : items) packet.putLong(i.id);
        packet.putLong(destination); packet.flip(); comm.receive(packet);
    }
    private static void finish() {
        int polls = 0;
        while (player.getActions().getLastSlowAction() != null) {
            if (++polls > 500) throw new AssertionError("Transfer did not finish");
            player.getActions().poll(player);
        }
        player.getActions().poll(player);
    }
    public static void main(String[] args) throws Exception {
        Logger.getLogger("MultiMove").setUseParentHandlers(false);
        immediate(); singleDrags(); serialActions(); cancellation(); limits(); independence(); inventorySlots(); inventoryDestinations();
        System.out.println("PASS: " + checks + " automatic-quantity and action-sequencing checks");
    }
    private static void immediate() throws Exception {
        reset(false);
        Item[] rows = new Item[100];
        for (int i = 0; i < rows.length; i++) rows[i] = item(i + 1, true);
        drag(rows);
        check(comm.moves.size() == 100 && comm.attempts.size() == 100, "every selected bulk row is handled once");
        check(comm.dialogs.isEmpty(), "automatic selection never calls the native dialog body");
        check(Questions.active.isEmpty(), "automatically answered questions are removed");
        check(RemoveItemQuestion.answersReceived.size() == 100, "all bulk rows pass through native quantity answers");
        for (int i = 0; i < 100; i++) {
            check(rows[i].units == 0, "native maximum transfers the complete available stack");
            check(RemoveItemQuestion.answersReceived.get(i) == Integer.MAX_VALUE, "uses the native max sentinel");
            check(comm.moves.get(i) == rows[i].id, "selection order preserved without duplicate requests");
        }
        check(RemoveItemQuestion.maximumDepth == 1, "automatic answers do not recursively drain the selection");
        check(comm.nativeCalls == 100, "sendQuestion early return bypasses the old manual waiting epilogue");
    }
    private static void singleDrags() throws Exception {
        reset(false);
        Item one = item(1, true);
        drag(one);
        check(comm.dialogs.isEmpty() && comm.moves.equals(Arrays.asList(one.id)), "single bulk drag automatically uses the native maximum without a dialog");
        check(one.units == 0 && RemoveItemQuestion.answersReceived.equals(Arrays.asList(Integer.MAX_VALUE)), "single drag uses the same native maximum as a multi-selection");
        check(Questions.active.isEmpty(), "single drag leaves no unanswered hidden question");
        Item other = item(2, true);
        new RemoveItemQuestion(player, other, target).sendQuestion();
        check(comm.dialogs.size() == 1 && Questions.active.contains(player.current), "unrelated bulk questions remain visible and registered");
        Properties two = new Properties(); two.setProperty("numstext", "2");
        Question q = player.current; q.answer(two); Questions.removeQuestion(q);
        check(other.units == 3, "unrelated question still honors a manually chosen quantity");
        reset(true);
        one = item(1, true); drag(one);
        check(comm.dialogs.isEmpty() && comm.attempts.size() == 1 && comm.moves.isEmpty(), "single timed bulk move starts without a dialog");
        finish();
        check(one.units == 0 && comm.moves.equals(Arrays.asList(one.id)), "single native timed action finishes normally");
    }
    private static void serialActions() throws Exception {
        reset(true);
        Item[] rows = new Item[14];
        for (int i = 0; i < 14; i++) rows[i] = item(i + 1, true);
        drag(rows);
        check(comm.attempts.size() == 1 && comm.moves.isEmpty(), "first native timed transfer starts before the next row");
        check(RemoveItemQuestion.answersReceived.size() == 1, "one native action is scheduled initially");
        finish();
        check(comm.moves.size() == 14 && comm.attempts.size() == 14, "14 timed bulk rows finish from one drag without prompts");
        check(player.getActions().maxQueued == 1 && player.getActions().rejected == 0, "strict native action-queue limit is respected");
        check(comm.dialogs.isEmpty() && Questions.active.isEmpty(), "timed selection shows no quantity dialogs");
        for (int i = 0; i < rows.length; i++) check(comm.moves.get(i) == rows[i].id && rows[i].units == 0, "native timer order and quantities");

        reset(true);
        player.getActions().addAction(new Action(player, null));
        Item a = item(1, true), b = item(2, true);
        drag(a, b);
        check(RemoveItemQuestion.answersReceived.isEmpty() && comm.dialogs.isEmpty(), "busy player defers the hidden native quantity answer");
        finish();
        check(comm.moves.equals(Arrays.asList(a.id, b.id)) && player.getActions().rejected == 0,
                "existing work finishes before selected bulk work is submitted");
    }
    private static void cancellation() throws Exception {
        reset(true);
        drag(item(1, true), item(2, true), item(3, true));
        player.getActions().clear(); finish();
        check(comm.moves.isEmpty() && comm.attempts.size() == 1, "stopping the native action cancels all remaining selected work");

        reset(true);
        Item a = item(1, true), b = item(2, true), c = item(3, false);
        drag(a, b); drag(c); finish();
        check(!comm.attempts.contains(b.id) && comm.moves.contains(c.id), "new drag supersedes the remaining batch without duplicating the running native action");

        reset(true);
        player.getActions().addAction(new Action(player, null));
        drag(item(1, true), item(2, true));
        Question hidden = player.current;
        player.getActions().clear(); finish();
        check(Questions.active.isEmpty() && comm.moves.isEmpty(), "stop removes the deferred automatic question");
        check(!Questions.active.contains(hidden) && RemoveItemQuestion.answersReceived.isEmpty(), "deferred question is never answered after cancellation");

        reset(true);
        player.getActions().addAction(new Action(player, null));
        drag(item(1, true), item(2, true));
        player.current.timedOut(); finish();
        check(comm.moves.isEmpty() && RemoveItemQuestion.answersReceived.isEmpty(), "expired hidden quantity question cannot resume automatically");

        for (int state = 0; state < 3; state++) {
            reset(true);
            player.getActions().addAction(new Action(player, null));
            drag(item(1, true), item(2, true));
            if (state == 0) player.linked = false;
            if (state == 1) player.dead = true;
            if (state == 2) player.undead = true;
            finish();
            check(comm.moves.isEmpty() && RemoveItemQuestion.answersReceived.isEmpty(), "disconnect/death cancels deferred answers");
        }
    }
    private static void limits() throws Exception {
        reset(false); target.transferLimit = 2;
        Item a = item(1, true), b = item(2, true);
        drag(a, b);
        check(a.units == 3 && b.units == 3, "native carry/quantity limits still control an automatic max answer");
        reset(false); target.capacity = 1;
        a = item(1, true); b = item(2, true); drag(a, b);
        check(a.units == 0 && b.units == 5 && comm.moves.size() == 1, "native capacity rejects the next bulk row");
        reset(true);
        a = item(1, true); b = item(2, true); drag(a, b);
        b.rejected = true; finish();
        check(comm.moves.equals(Arrays.asList(a.id)) && b.units == 5, "fresh native checks reject a later row after an earlier timed transfer");
    }
    private static void independence() throws Exception {
        reset(true);
        Item a = item(1, true), b = item(2, true); drag(a, b);
        Player second = new Player(); Communicator secondComm = new Communicator(second);
        Item c = item(3, true), d = item(4, true);
        ByteBuffer packet = ByteBuffer.allocate(26);
        packet.putShort((short)2).putLong(c.id).putLong(d.id).putLong(target.id); packet.flip();
        secondComm.receive(packet);
        finish();
        check(secondComm.attempts.size() == 1 && comm.moves.size() == 2, "one player's completion cannot advance another player's selection");
        for (int i = 0; i < 10; i++) second.getActions().poll(second);
        check(secondComm.moves.equals(Arrays.asList(c.id, d.id)) && secondComm.dialogs.isEmpty(), "independent automatic transfers finish for each player");
    }

    private static void inventorySlots() throws Exception {
        reset(false); target.inventory = true; target.capacity = 100; target.contained = 97;
        Item one = item(1, true); one.units = 50;
        drag(one);
        check(target.contained == 100 && one.units == 47, "single large stack fills only three currently free inventory slots");
        check(comm.dialogs.isEmpty() && comm.moves.size() == 1, "slot-limited single withdrawal has no popup");

        reset(false); target.inventory = true; target.capacity = 100; target.contained = 95;
        Item a = item(1, true), b = item(2, true), c = item(3, true);
        a.units = 2; b.units = 20; c.units = 20;
        drag(a, b, c);
        check(a.units == 0 && b.units == 17 && c.units == 20, "later bulk rows see the slot count left by preceding withdrawals");
        check(target.contained == 100 && comm.moves.equals(Arrays.asList(a.id, b.id)), "mixed bulk selection never exceeds currently available slots");

        reset(false); target.inventory = true; target.capacity = 100; target.contained = 100;
        one = item(1, true); one.units = 50;
        drag(one);
        check(target.contained == 100 && one.units == 50 && comm.moves.isEmpty(), "full inventory transfers nothing and leaves the source intact");
        check(comm.dialogs.isEmpty(), "full inventory does not trigger a quantity popup");

        reset(false); target.inventory = true; target.capacity = 100; target.contained = 97;
        Item[] ordinary = new Item[14];
        for (int i = 0; i < ordinary.length; i++) ordinary[i] = item(i + 1, false);
        drag(ordinary);
        check(target.contained == 100 && comm.moves.size() == 3, "ordinary selection honors the native three-slot limit too");
        for (int i = 0; i < ordinary.length; i++) {
            check(ordinary[i].parent == (i < 3 ? target.id : -10L), "untransferred ordinary items stay in their source");
        }

        for (int occupied : new int[] {100, 99, 90}) {
            reset(false); target.inventory = true; target.capacity = 100; target.contained = 95;
            player.getActions().addAction(new Action(player, null));
            one = item(1, true); one.units = 50; drag(one);
            check(comm.moves.isEmpty() && RemoveItemQuestion.answersReceived.isEmpty(), "quantity is deferred while native work is active");
            target.contained = occupied;
            finish();
            check(one.units == 50 - (100 - occupied), "withdrawal uses the free slots at execution rather than the drag's old count");
            check(target.contained == 100 && comm.dialogs.isEmpty(), "live slot changes never overfill inventory or show a dialog");
        }

        reset(false); target.inventory = true; target.capacity = 100; target.contained = 95; target.transferLimit = 2;
        one = item(1, true); one.units = 50; drag(one);
        check(target.contained == 97 && one.units == 48, "weight/carry limits can reduce the withdrawal below available slot capacity");
    }

    private static long inventoryDestination(int form, int occupied) {
        reset(false);
        target = new Item(3000 * 256L + 6L);
        target.hollow = true; target.inventory = true;
        target.capacity = 100; target.contained = occupied;
        player.inventory = target;
        if (form == 0) return target.id;
        if (form == 1) return -1L;
        if (form == 2) {
            Item group = new Item(id(3001));
            group.template = 1392; group.parent = target.id;
            return group.id;
        }
        Item row = new Item(id(3002)); row.parent = target.id;
        if (form == 3) return row.id;
        Item leaf = new Item(id(3003)); leaf.parent = row.id;
        return leaf.id;
    }

    private static void inventoryDestinations() throws Exception {
        for (int form = 0; form < 5; form++) {
            long destination = inventoryDestination(form, 97);
            Item one = item(1, true); one.units = 50;
            dragTo(destination, one);
            check(comm.dialogs.isEmpty(), "inventory destination form " + form + " suppresses a single-row quantity dialog");
            check(one.units == 47 && target.contained == 100, "inventory destination form " + form + " fills only live free slots");
            check(comm.nativeCalls == 1 && Questions.active.isEmpty(), "inventory aliases finish without repeated requests or hidden questions");

            destination = inventoryDestination(form, 95);
            Item a = item(1, true), b = item(2, false), c = item(3, true);
            a.units = 2; c.units = 20;
            dragTo(destination, a, b, c);
            check(comm.dialogs.isEmpty() && comm.moves.equals(Arrays.asList(a.id, b.id, c.id)), "mixed inventory alias selection processes every row without a dialog");
            check(a.units == 0 && b.parent == target.id && c.units == 18 && target.contained == 100, "ordinary and bulk items share the inventory's free slots");

            destination = inventoryDestination(form, 100);
            a = item(1, true); b = item(2, true);
            dragTo(destination, a, b);
            check(comm.moves.isEmpty() && comm.dialogs.isEmpty() && a.units == 5 && b.units == 5, "full inventory alias leaves both source rows untouched without dialogs");
            check(comm.attempts.size() == 2, "full inventory aliases still check each selected row once");

            destination = inventoryDestination(form, 95);
            player.getActions().addAction(new Action(player, null));
            one = item(1, true); one.units = 50;
            dragTo(destination, one);
            check(RemoveItemQuestion.answersReceived.isEmpty() && comm.dialogs.isEmpty(), "inventory alias waits silently for existing work");
            target.contained = 99; finish();
            check(one.units == 49 && target.contained == 100 && comm.dialogs.isEmpty(), "deferred inventory alias uses current slots when it runs");

            destination = inventoryDestination(form, 90);
            a = item(1, false); a.parent = target.id; b = item(2, false);
            dragTo(destination, a, b);
            check(comm.moves.equals(Arrays.asList(b.id)) && comm.nativeCalls == 1, "inventory aliases skip items already in inventory");

            destination = inventoryDestination(form, 90);
            dragTo(destination, target, item(1, false));
            check(comm.attempts.isEmpty() && comm.nativeCalls == 0, "inventory aliases reject moving the selection into itself before any transfer");
        }

        reset(false);
        Item inventory = new Item(3000 * 256L + 6L); inventory.hollow = true;
        inventory.inventory = true; inventory.capacity = 100; player.inventory = inventory;
        target.parent = inventory.id;
        Item row = item(4, false); row.parent = target.id;
        Item bulk = item(1, true), ordinary = item(2, false);
        dragTo(row.id, bulk, ordinary);
        check(comm.dialogs.isEmpty() && comm.moves.equals(Arrays.asList(bulk.id, ordinary.id)), "drop onto an item in a carried bag transfers the complete selection without questions");
        check(target.contained == 2 && ordinary.parent == target.id && inventory.contained == 0, "drop onto an item chooses the nearest bag rather than the root inventory");

        reset(false); target.hollow = false;
        drag(item(1, true), item(2, true));
        check(comm.dialogs.size() == 1 && comm.moves.isEmpty(), "unresolvable non-container destination retains its native quantity question");
        check(comm.nativeCalls == 1 && comm.attempts.size() == 1, "unsupported destination is not partially resubmitted");
    }
}
