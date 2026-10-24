package mod.wurmunlimited.multimove;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.Logger;
import com.wurmonline.server.Items;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.questions.*;
import org.gotti.wurmunlimited.modcomm.IChannelListener;

/** Real ModComm listener driving transformed native-shaped transfer fixtures. */
public final class QuantityScenarios {
    private static int checks;
    private static Player player;
    private static Communicator comm;
    private static Item target;
    private static IChannelListener listener;
    private static boolean automatic;
    private static long id(int n) { return n * 256L + 2L; }
    private static void check(boolean ok, String text) {
        checks++;
        if (!ok) throw new AssertionError(text);
    }
    private static void reset(boolean timed) {
        Items.items.clear(); Questions.active.clear(); RemoveItemQuestion.answersReceived.clear();
        player = new Player(); comm = new Communicator(player);
        target = new Item(id(2000)); target.hollow = true; target.bulkContainer = timed;
    }
    private static Item item(int n, boolean bulk) {
        Item i = new Item(id(n)); i.bulk = bulk; i.units = 10; return i;
    }
    private static ByteBuffer request(int quantity, long destination, Item... rows) {
        ByteBuffer b = ByteBuffer.allocate(15 + 8 * rows.length);
        b.put((byte)1).putInt(quantity).putShort((short)rows.length);
        for (Item i : rows) b.putLong(i.id);
        b.putLong(destination); b.flip(); return b;
    }
    private static void send(int quantity, Item... rows) {
        ByteBuffer b = request(quantity, target.id, rows);
        int position = b.position(); listener.handleMessage(player, b);
        check(b.position() == position, "quantity parsing leaves the transport buffer position intact");
    }
    private static void normal(Item... rows) throws Exception {
        ByteBuffer b = ByteBuffer.allocate(10 + 8 * rows.length);
        b.putShort((short)rows.length);
        for (Item i : rows) b.putLong(i.id);
        b.putLong(target.id); b.flip(); comm.receive(b);
    }
    private static void finish(Player p) {
        int polls = 0;
        while (p.getActions().getLastSlowAction() != null) {
            if (++polls > 500) throw new AssertionError("Timed transfer did not finish");
            p.getActions().poll(p);
        }
        p.getActions().poll(p);
    }
    public static void main(String[] args) throws Exception {
        Logger.getLogger("MultiMove").setUseParentHandlers(false);
        automatic = args[0].equals("automatic");
        MultiMoveMod.registerQuantityChannel();
        Class<?> modComm = Class.forName("org.gotti.wurmunlimited.modcomm.ModComm");
        Field channels = modComm.getDeclaredField("channels"); channels.setAccessible(true);
        Object channel = ((Map<?, ?>)channels.get(null)).get("multimove.quantity.v1");
        Field field = channel.getClass().getDeclaredField("listener"); field.setAccessible(true);
        listener = (IChannelListener)field.get(channel);
        quantities(); inventory(); timed(); malformed(); cancellation(); isolation();
        if (args.length > 1) {
            reset(false); target.inventory = true; target.capacity = 100; target.contained = 97;
            player.inventory = target; Item a = item(1, true);
            byte[] packet = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[1]));
            listener.handleMessage(player, ByteBuffer.wrap(packet));
            check(a.units == 8 && target.contained == 99, "packet produced by the actual client hook transfers the chosen two items");
            check(RemoveItemQuestion.answersReceived.equals(Arrays.asList(2)) && comm.dialogs.isEmpty(), "client-to-server payload retains quantity without a second dialog");
        }
        System.out.println("PASS: " + checks + " explicit-quantity/channel checks with autoQuantities=" + automatic);
    }
    private static void quantities() throws Exception {
        for (int quantity : new int[] {1, 2, 7, Integer.MAX_VALUE}) {
            reset(false); Item a = item(1, true); send(quantity, a);
            check(a.units == 10 - Math.min(10, quantity), "entered bulk quantity reaches the native answer unchanged");
            check(RemoveItemQuestion.answersReceived.equals(Arrays.asList(quantity)), "explicit quantity is never replaced with the automatic maximum");
            check(comm.dialogs.isEmpty() && Questions.active.isEmpty(), "the client quantity dialog does not cause a second server dialog");
        }
        reset(false); Item a = item(1, true), b = item(2, true), c = item(3, false);
        send(3, a, b, c);
        check(a.units == 7 && b.units == 7 && c.parent == target.id, "quantity applies per selected bulk row and ordinary listed IDs move normally");
        check(comm.moves.equals(Arrays.asList(a.id, b.id, c.id)) && comm.dialogs.isEmpty(), "mixed quantity requests retain order without questions");

        reset(false); a = item(1, false); b = item(2, false); c = item(3, false);
        send(2, a, b);
        check(comm.moves.equals(Arrays.asList(a.id, b.id)) && c.parent == -10, "client-clipped ordinary ID list does not expand to other items");

        reset(false); a = item(1, true); send(2, a);
        b = item(2, true); normal(b);
        if (automatic) check(b.units == 0 && comm.dialogs.isEmpty(), "explicit quantity cannot leak into a later normal drag");
        else check(b.units == 10 && comm.dialogs.size() == 1, "normal drags still respect autoQuantities=false after an explicit request");
    }
    private static void inventory() {
        for (int quantity : new int[] {2, 8}) {
            reset(false); target.inventory = true; target.capacity = 100;
            target.contained = 97; player.inventory = target;
            Item a = item(1, true);
            listener.handleMessage(player, request(quantity, -1L, a));
            int moved = Math.min(3, quantity);
            check(a.units == 10 - moved && target.contained == 97 + moved, "explicit inventory quantity stays within the entered amount and current slots");
            check(comm.dialogs.isEmpty(), "explicit inventory withdrawal does not open a second question");
        }
    }
    private static void timed() {
        reset(true); Item a = item(1, true), b = item(2, true), c = item(3, true);
        send(2, a, b, c); finish(player);
        check(a.units == 8 && b.units == 8 && c.units == 8, "every timed bulk action retains the entered quantity");
        check(RemoveItemQuestion.answersReceived.equals(Arrays.asList(2, 2, 2)), "later timed rows do not revert to maximum");
        check(player.getActions().maxQueued == 1 && player.getActions().rejected == 0 && comm.dialogs.isEmpty(), "quantity transfers retain native serial action limits");

        reset(false); target.inventory = true; target.capacity = 100; target.contained = 90;
        player.getActions().addAction(new Action(player, null));
        a = item(1, true); send(7, a); target.contained = 98; finish(player);
        check(a.units == 8 && target.contained == 100 && comm.dialogs.isEmpty(), "deferred explicit quantity uses the live slot limit");
        check(RemoveItemQuestion.answersReceived.equals(Arrays.asList(7)), "deferral preserves the entered quantity");
    }
    private static void malformed() {
        reset(false); Item a = item(1, true);
        List<ByteBuffer> bad = new ArrayList<>();
        for (int n = 0; n < 23; n++) bad.add(ByteBuffer.allocate(n));
        bad.add(ByteBuffer.allocate(816));
        bad.add(request(0, target.id, a)); bad.add(request(-1, target.id, a));
        ByteBuffer version = request(2, target.id, a); version.put(0, (byte)2); bad.add(version);
        ByteBuffer count = request(2, target.id, a); count.putShort(5, (short)101); bad.add(count);
        ByteBuffer extra = ByteBuffer.allocate(24); extra.put(request(2, target.id, a)).put((byte)1).flip(); bad.add(extra);
        for (ByteBuffer b : bad) {
            listener.handleMessage(player, b);
            check(comm.nativeCalls == 0 && comm.dialogs.isEmpty() && a.units == 10, "malformed quantity payload cannot start or silently maximize a move");
        }
        listener.handleMessage(player, request(2, 1L, a));
        check(comm.nativeCalls == 0 && comm.moves.isEmpty(), "unsupported quantity destination has no native maximum fallback");

        reset(true); player.getActions().addAction(new Action(player, null));
        a = item(1, true); send(2, a);
        listener.handleMessage(player, request(0, target.id, a)); finish(player);
        check(a.units == 8, "malformed request cannot replace a valid deferred transfer");
    }
    private static void cancellation() {
        reset(true); Item a = item(1, true), b = item(2, true), c = item(3, true);
        send(2, a, b); send(4, c); finish(player);
        check(a.units == 8 && b.units == 10 && c.units == 6, "new explicit request replaces remaining work without changing the already-running action");
        reset(true); a = item(1, true); b = item(2, true); send(2, a, b);
        player.getActions().clear(); finish(player);
        check(a.units == 10 && b.units == 10 && comm.attempts.size() == 1, "Stop cancels an explicit selection without resubmitting it");

        reset(false); a = item(1, true); a.rejected = true; b = item(2, true); send(2, a, b);
        check(a.units == 10 && b.units == 8, "quantity channel still uses native permission/rejection checks for every item");
    }
    private static void isolation() {
        reset(true); Item a = item(1, true), b = item(2, true); send(2, a, b);
        Player other = new Player(); Communicator otherComm = new Communicator(other);
        Item c = item(3, true), d = item(4, true);
        listener.handleMessage(other, request(4, target.id, c, d));
        finish(player); finish(other);
        check(a.units == 8 && b.units == 8 && c.units == 6 && d.units == 6, "authenticated channel players have independent quantities and queues");
        check(comm.dialogs.isEmpty() && otherComm.dialogs.isEmpty(), "both players complete without additional questions");
    }
}
