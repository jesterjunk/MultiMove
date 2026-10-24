package mod.wurmunlimited.multimove;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.Logger;
import com.wurmonline.server.*;
import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.questions.*;

public final class Scenarios {
    private static int checks;
    private static Player player;
    private static Communicator comm;
    private static Item target;
    private static long id(int n) { return n * 256L + 2L; }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void reset() {
        Items.items.clear();
        Questions.active.clear();
        player = new Player();
        comm = new Communicator(player);
        target = new Item(id(2000));
        target.hollow = true;
    }
    private static Item item(int n, boolean bulk) {
        Item item = new Item(id(n));
        item.bulk = bulk;
        return item;
    }
    private static ByteBuffer packet(long dest, long... ids) {
        ByteBuffer b = ByteBuffer.allocate(10 + ids.length * 8);
        b.putShort((short)ids.length);
        for (long id : ids) b.putLong(id);
        b.putLong(dest);
        b.flip();
        return b;
    }
    private static void drag(Item... items) throws Exception {
        long[] ids = new long[items.length];
        for (int i = 0; i < items.length; i++) ids[i] = items[i].id;
        comm.receive(packet(target.id, ids));
    }
    private static void answer(String count) {
        Question q = player.current;
        if (!Questions.active.contains(q)) throw new AssertionError("Quantity question is not active");
        Properties values = new Properties();
        if (count != null) values.setProperty("items", count);
        q.answer(values);
        // This order is the native BML handler's order, including removal after answer.
        Questions.removeQuestion(q);
    }
    public static void main(String[] args) throws Exception {
        Logger.getLogger("MultiMove").setUseParentHandlers(false);
        if (args.length > 0) {
            reset();
            drag(item(1, true), item(2, true), item(3, false));
            check(comm.attempts.size() == 1 && comm.dialogs.size() == 1,
                    "unpatched fixture reproduces the server's first-bulk-row early return");
            answer("1");
            check(comm.moves.equals(Arrays.asList(id(1))) && comm.attempts.size() == 1,
                    "unpatched quantity answer does not resume the rest of the request");
            System.out.println("PASS: unpatched bulk early-return regression reproduced");
            return;
        }
        ordinary(); bulkSequence(); cancellations(); safety(); failures(); boundaries(); isolation();
        System.out.println("PASS: " + checks + " behavioral checks against transformed server fixtures");
    }

    private static void ordinary() throws Exception {
        reset();
        Item[] many = new Item[14];
        for (int i = 0; i < many.length; i++) many[i] = item(i + 1, false);
        drag(many);
        check(comm.nativeCalls == 14 && comm.moves.size() == 14, "14 highlighted items each use the native handler once");
        for (int i = 0; i < 14; i++) {
            check(comm.moves.get(i) == id(i + 1), "exact item order");
            check(many[i].parent == target.id, "only the native handler changes the parent");
        }
        reset();
        Item a = item(1, false), b = item(2, false), c = item(3, false);
        b.rejected = true;
        drag(a, b, c);
        check(comm.attempts.size() == 3, "one native rejection does not discard later selected items");
        check(comm.moves.equals(Arrays.asList(a.id, c.id)), "native rejection is honored");
        check(b.parent == -10 && comm.messages.contains("Native move rejected"), "native failure message and ownership are preserved");
    }

    private static void bulkSequence() throws Exception {
        reset();
        Item a = item(1, true), b = item(2, true), c = item(3, false);
        drag(a, b, c);
        check(comm.dialogs.size() == 1 && comm.moves.isEmpty(), "first bulk dialog is shown without moving anything automatically; dialogs="
                + comm.dialogs.size() + " moves=" + comm.moves.size() + " attempts=" + comm.attempts.size());
        answer("2");
        check(a.units == 3 && b.units == 5, "native quantity is honored for each stack");
        check(comm.dialogs.size() == 2 && comm.attempts.size() == 2, "second bulk dialog is shown only after answering the first");
        check(Questions.active.contains(player.current), "native removal of the old question keeps the next question active");
        answer("2147483647");
        check(b.units == 0 && comm.moves.equals(Arrays.asList(a.id, b.id, c.id)), "max answer and remaining ordinary item are handled natively");
        check(comm.attempts.size() == 3 && comm.nativeCalls == 3, "bulk completion does not repeat requests");

        reset();
        Item[] many = new Item[14];
        for (int i = 0; i < 14; i++) many[i] = item(i + 1, true);
        drag(many);
        for (int i = 0; i < 14; i++) {
            check(comm.dialogs.size() == i + 1, "one bulk question at a time for 14 types");
            answer("1");
        }
        check(comm.moves.size() == 14 && comm.nativeCalls == 14, "all 14 bulk rows can complete from one drag");
        check(new HashSet<Long>(comm.moves).size() == 14, "bulk IDs are attempted once");

        reset();
        a = item(1, true); b = item(2, false);
        drag(a, b);
        a.rejected = true;
        answer("1");
        check(comm.moves.equals(Arrays.asList(b.id)) && a.units == 5, "rejection after opening a quantity dialog stays native");
    }

    private static void cancellations() throws Exception {
        for (String value : new String[] {"0", "bad", "-1", null}) {
            reset();
            drag(item(1, true), item(2, false));
            answer(value);
            check(comm.moves.isEmpty() && comm.attempts.size() == 1, "zero, cancel, invalid and missing quantity stop the remaining selection");
        }
        reset();
        drag(item(1, true), item(2, false));
        Question pending = player.current;
        Questions.removeQuestion(pending);
        Properties values = new Properties(); values.setProperty("items", "1");
        // An invalid callback cannot restart the discarded batch.
        pending.answer(values);
        check(comm.attempts.size() == 1, "closed/removed quantity dialog discards remaining items");
        reset();
        drag(item(1, true), item(2, false));
        player.current.timedOut();
        answer("1");
        check(comm.attempts.size() == 1, "timed-out quantity dialog cannot resume a batch");
        reset();
        drag(item(1, true), item(2, false));
        new RemoveItemQuestion(player, item(9, true), target).sendQuestion();
        answer("1");
        check(comm.attempts.size() == 1 && !comm.moves.contains(id(2)), "unrelated dialog replacement cancels the batch");
        reset();
        drag(item(1, true), item(2, false));
        pending = player.current;
        drag(item(3, false));
        check(!Questions.active.contains(pending), "new drag invalidates the previous quantity question");
        check(comm.moves.equals(Arrays.asList(id(3))) && comm.attempts.size() == 2, "new single drag supersedes a waiting selection");
        for (int condition = 0; condition < 3; condition++) {
            reset();
            drag(item(1, true), item(2, false));
            if (condition == 0) player.linked = false;
            if (condition == 1) player.dead = true;
            if (condition == 2) player.undead = true;
            answer("1");
            check(comm.moves.isEmpty() && comm.attempts.size() == 1, "disconnect and death stop further queued transfers");
        }
    }

    private static void safety() throws Exception {
        reset();
        Item a = item(1, false), b = item(2, false);
        comm.receive(packet(target.id, a.id, a.id, b.id));
        check(comm.moves.equals(Arrays.asList(a.id, b.id)), "duplicate IDs are removed");
        reset();
        Item bag = item(1, false), child = item(2, false), other = item(3, false);
        bag.hollow = true; child.parent = bag.id;
        drag(child, bag, other);
        check(comm.moves.equals(Arrays.asList(bag.id, other.id)) && child.parent == bag.id, "selected parent carries its child once");
        reset();
        bag = item(1, false); bag.hollow = true;
        target.parent = bag.id;
        drag(bag, item(2, false));
        check(comm.attempts.isEmpty(), "destination inside a selected container cancels everything");
        reset();
        drag(target, item(1, false));
        check(comm.attempts.isEmpty(), "dropping onto a selected destination cancels everything");
        reset();
        a = item(1, false); a.parent = target.id;
        b = item(2, false);
        comm.receive(packet(target.id, a.id, b.id, id(9999)));
        check(comm.moves.equals(Arrays.asList(b.id)), "already-there and stale items are skipped");
    }

    private static void failures() throws Exception {
        reset();
        Item a = item(1, false), b = item(2, false), c = item(3, false);
        b.fail = true;
        drag(a, b, c);
        check(comm.moves.equals(Arrays.asList(a.id)) && comm.nativeCalls == 2, "submission error stops once without retry or fallback");
        check(c.parent == -10, "unattempted item remains untouched after error");
        reset();
        target.capacity = 1;
        a = item(1, false); b = item(2, false); c = item(3, false);
        drag(a, b, c);
        check(comm.moves.equals(Arrays.asList(a.id)) && comm.messages.size() == 2, "native capacity checks remain authoritative");
        check(b.parent == -10 && c.parent == -10, "full destination cannot force inventory mutation");
    }

    private static void boundaries() throws Exception {
        reset();
        drag(item(1, true));
        answer("1");
        check(comm.nativeCalls == 1 && comm.dialogs.size() == 1, "manual mode keeps the native single-stack quantity dialog");
        reset();
        target.hollow = false;
        drag(item(1, false), item(2, false));
        check(comm.nativeCalls == 1, "non-container destination keeps original packet handling");
        reset();
        Item bank = new Item(5000 * 256L + 13);
        bank.hollow = true;
        Item a = item(1, false), b = item(2, false);
        comm.receive(packet(bank.id, a.id, b.id));
        check(comm.nativeCalls == 1, "bank destination retains original packet handling");
        reset();
        Item[] hundred = new Item[100];
        for (int i = 0; i < hundred.length; i++) hundred[i] = item(i + 1, false);
        drag(hundred);
        check(comm.moves.size() == 100 && comm.nativeCalls == 100, "native 100-item limit boundary");
        reset();
        Item[] excess = new Item[101];
        for (int i = 0; i < excess.length; i++) excess[i] = item(i + 1, false);
        drag(excess);
        check(comm.moves.size() == 100 && comm.nativeCalls == 1, "oversized request retains the native limit");
    }

    private static void isolation() throws Exception {
        reset();
        drag(item(1, true), item(2, false));
        Player second = new Player();
        Communicator other = new Communicator(second);
        Item a = item(3, false), b = item(4, false);
        other.receive(packet(target.id, a.id, b.id));
        check(other.moves.equals(Arrays.asList(a.id, b.id)) && comm.attempts.size() == 1,
                "one player's quantity dialog does not block another player's selection");
        answer("1");
        check(comm.moves.equals(Arrays.asList(id(1), id(2))), "each communicator retains only its own pending selection");
    }
}
