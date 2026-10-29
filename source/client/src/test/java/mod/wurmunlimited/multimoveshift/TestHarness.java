package mod.wurmunlimited.multimoveshift;

import java.lang.reflect.*;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import javassist.*;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modcomm.IChannelListener;
import org.gotti.wurmunlimited.modcomm.ModComm;
import org.gotti.wurmunlimited.modcomm.ModCommHandler;

public final class TestHarness {
    private static final String INVENTORY = "com.wurmonline.client.renderer.gui.InventoryListComponent";
    private static int checks;
    private static Recording transport;
    private static Class<?> inventoryClass, rowClass;
    private static Object source, destination, row;
    private static void check(boolean ok, String text) {
        checks++;
        if (!ok) throw new AssertionError(text);
    }
    static final class Recording implements MultiMoveShiftMod.Transport {
        boolean active = true, fail;
        int attempts;
        final List<byte[]> sent = new ArrayList<>();
        final List<String> notices = new ArrayList<>();
        public boolean active() { return active; }
        public void send(ByteBuffer message) {
            attempts++;
            if (fail) throw new IllegalStateException("Test send failure");
            byte[] bytes = new byte[message.remaining()]; message.get(bytes); sent.add(bytes);
        }
        public void notice(Object destination, String text) { notices.add(text); }
    }
    private static void reset(long[]... groups) throws Exception {
        source = inventoryClass.newInstance(); destination = inventoryClass.newInstance();
        inventoryClass.getField("groups").set(source, groups);
        row = rowClass.getConstructor(inventoryClass).newInstance(source);
        transport.sent.clear(); transport.notices.clear(); transport.attempts = 0;
        transport.active = true; transport.fail = false;
    }
    private static void choose(long target, int amount) throws Exception {
        Class<?> base = rowClass.getSuperclass();
        inventoryClass.getMethod("chooseAmount", base, long.class, int.class).invoke(destination, row, target, amount);
    }
    private static int nativeCalls() throws Exception { return inventoryClass.getField("nativeCalls").getInt(destination); }
    private static void payload(int quantity, long target, long... expected) {
        check(transport.sent.size() == 1 && transport.attempts == 1, "one explicit request is sent without duplicate native fallback");
        ByteBuffer b = ByteBuffer.wrap(transport.sent.get(0));
        check(b.get() == 1 && b.getInt() == quantity && (b.getShort() & 65535) == expected.length, "protocol carries the actual entered amount and ID count");
        for (long id : expected) check(b.getLong() == id, "selected source ID and order are retained");
        check(b.getLong() == target && !b.hasRemaining(), "target aliases are preserved and payload has no trailing data");
    }
    private static void handshake(String name, int channelId) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(2); out.writeByte(1); out.writeInt(channelId > 0 ? 1 : 0);
        if (channelId > 0) { out.writeInt(channelId); out.writeUTF(name); }
        out.flush(); ModCommHandler.handlePacket(ByteBuffer.wrap(bytes.toByteArray()));
    }
    private static void connectionLifecycle() throws Throwable {
        final Object[] current = {new Object()};
        final boolean[] fail = {false};
        final int[] reads = {0};
        final MultiMoveShiftMod.ChannelTransport connected = new MultiMoveShiftMod.ChannelTransport(() -> {
            reads[0]++;
            if (fail[0]) throw new IllegalStateException("Connection lookup failed");
            return current[0];
        });
        String name = "multimove.test.quantity.v1";
        connected.channel = ModComm.registerChannel(name, new IChannelListener() {
            public void onServerConnected() { connected.serverConnected(); }
        });
        check(!connected.active() && reads[0] == 0, "startup without server confirmation does not inspect or change the game connection");
        handshake(name, 0);
        check(!connected.active() && reads[0] == 0, "a modded server without the MultiMove channel does not enable the companion");
        handshake(name, 37);
        check(connected.active(), "actual SDK handshake confirmation enables the current connection");
        current[0] = new Object();
        check(connected.channel.isActive() && !connected.active(), "switching servers ignores the SDK's stale active channel flag");
        handshake(name, 0);
        check(!connected.active(), "an unsupported server handshake cannot reuse the previous server's confirmation");

        reset(new long[] {258, 514, 770});
        Method nativeAmount = null;
        for (Method method : inventoryClass.getDeclaredMethods()) {
            if (method.getName().startsWith("itemDroppedAmount$")) nativeAmount = method;
        }
        if (nativeAmount == null) throw new AssertionError("Wrapped native method missing");
        nativeAmount.setAccessible(true);
        MultiMoveShiftMod.quantityHandler(connected).invoke(destination, nativeAmount, new Object[] {row, -1L, 2});
        check(nativeCalls() == 1 && Arrays.equals((long[])inventoryClass.getField("nativeIds").get(destination), new long[] {258, 514}),
                "a real stale SDK channel still passes the Shift action to the original game method");

        handshake(name, 72);
        check(connected.active(), "fresh confirmation enables the companion after reconnecting to a supporting server");
        current[0] = null;
        check(!connected.active(), "losing the current connection disables custom quantity handling");
        current[0] = new Object();
        check(!connected.active(), "a new connection remains native before its own confirmation");
        handshake(name, 91);
        check(connected.active(), "server support can be confirmed again after a lost connection");
        fail[0] = true;
        check(!connected.active(), "a failed connection lookup cannot leave the companion enabled");
        handshake(name, 92);
        check(!connected.active(), "a confirmation with no identifiable connection stays disabled");
        fail[0] = false;
        check(!connected.active(), "recovering connection access does not bypass the fresh-confirmation requirement");
        handshake(name, 93);
        check(connected.active(), "a later valid confirmation restores exact-quantity support");
    }
    public static void main(String[] args) throws Throwable {
        java.util.logging.Logger.getLogger("MultiMoveShift").setUseParentHandlers(false);
        java.util.logging.Logger.getLogger("ModComm").setUseParentHandlers(false);
        HookManager manager = HookManager.getInstance();
        ClassPool pool = manager.getClassPool();
        if (args[0].equals("--api")) {
            for (int i = 1; i < args.length; i++) pool.insertClassPath(args[i]);
            MultiMoveShiftMod.install(manager, new Recording());
            pool.get(INVENTORY).toBytecode();
            manager.getLoader().delegateLoadingOf("jdk.");
            manager.getLoader().delegateLoadingOf("org.gotti.");
            manager.getLoader().loadClass(INVENTORY).getDeclaredMethods();
            System.out.println("PASS: Shift hook compiles and patched client class loads against " + args[1]);
            return;
        }
        transport = new Recording();
        CtClass hookApi = pool.get(HookManager.class.getName());
        CtMethod invoke = hookApi.getDeclaredMethod("invoke");
        hookApi.removeMethod(invoke);
        CtMethod original = pool.get(INVENTORY).getDeclaredMethod("itemDroppedAmount");
        byte[] originalCode = original.getMethodInfo().getCodeAttribute().getCode().clone();
        try {
            MultiMoveShiftMod.install(manager, transport);
            throw new AssertionError("Accepted an incompatible hook API");
        } catch (RuntimeException expected) { }
        check(pool.get(INVENTORY).getDeclaredMethod("itemDroppedAmount") == original,
                "late hook failure restores the native method name");
        check(Arrays.equals(originalCode, original.getMethodInfo().getCodeAttribute().getCode()),
                "late hook failure retains the original native method body");
        for (CtMethod method : pool.get(INVENTORY).getDeclaredMethods()) {
            check(!method.getName().startsWith("itemDroppedAmount$"), "late hook failure leaves no renamed or duplicate native method");
        }
        hookApi.addMethod(invoke);
        MultiMoveShiftMod.install(manager, transport);
        manager.getLoader().delegateLoadingOf("jdk.");
        manager.getLoader().delegateLoadingOf("org.gotti.");
        inventoryClass = manager.getLoader().loadClass(INVENTORY);
        rowClass = manager.getLoader().loadClass(INVENTORY + "$SingleTreeListItem");

        reset(new long[] {258}); choose(-1L, 2); payload(2, -1L, 258);
        byte[] two = transport.sent.get(0);
        Files.write(Paths.get("build/shift-request.bin"), two);
        check(nativeCalls() == 0, "the lossy stock bulk submission is suppressed");
        reset(new long[] {258}); choose(-1L, 7); payload(7, -1L, 258);
        check(!Arrays.equals(two, transport.sent.get(0)), "different bulk quantities no longer produce identical requests");
        reset(new long[] {258, 514, 770, 1026}, new long[] {1282, 1538, 1794});
        choose(9990L, 2); payload(2, 9990L, 258, 514, 1282, 1538);
        check(nativeCalls() == 0, "ordinary stacks keep the native per-row selected-ID limit");
        for (int amount : new int[] {0, -1}) {
            reset(new long[] {258}); choose(-1L, amount);
            check(transport.sent.isEmpty() && nativeCalls() == 0 && !transport.notices.isEmpty(), "zero and negative values cannot turn into maximum transfers");
        }
        reset(new long[] {258}); transport.active = false; choose(-1L, 2);
        check(nativeCalls() == 1 && transport.sent.isEmpty() && transport.notices.isEmpty(), "unsupported server runs the stock Shift action exactly once without custom packets or messages");
        check(inventoryClass.getField("nativeTarget").getLong(destination) == -1L
                && inventoryClass.getField("nativeAmount").getInt(destination) == 2,
                "native passthrough retains the original destination and entered amount");
        check(Arrays.equals((long[])inventoryClass.getField("nativeIds").get(destination), new long[] {258}),
                "unsupported bulk row retains the stock ID request");
        reset(new long[] {258, 514, 770}, new long[] {1026, 1282, 1538});
        transport.active = false; choose(9990L, 2);
        check(nativeCalls() == 1 && Arrays.equals((long[])inventoryClass.getField("nativeIds").get(destination),
                new long[] {258, 514, 1026, 1282}), "unsupported server keeps native per-stack quantity clipping");
        for (int amount : new int[] {0, -1}) {
            reset(new long[] {258, 514}); transport.active = false; choose(-1L, amount);
            check(nativeCalls() == 1 && transport.notices.isEmpty() && transport.sent.isEmpty()
                    && inventoryClass.getField("nativeAmount").getInt(destination) == amount,
                    "unsupported server keeps the stock handling of zero and negative values");
        }
        reset(new long[101]); transport.active = false; choose(-1L, Integer.MAX_VALUE);
        check(nativeCalls() == 1 && transport.notices.isEmpty() && transport.sent.isEmpty(),
                "unsupported server uses native selection bounds rather than the companion's limit");
        reset(); transport.active = false; choose(-1L, 2);
        check(nativeCalls() == 1 && transport.notices.isEmpty() && transport.sent.isEmpty(),
                "unsupported empty selection goes through the original method");
        reset(new long[] {258}); transport.active = false;
        inventoryClass.getField("nativeFail").setBoolean(destination, true);
        try { choose(-1L, 2); throw new AssertionError("Swallowed native failure"); }
        catch (InvocationTargetException expected) {
            Throwable cause = expected.getCause();
            while (cause.getCause() != null) cause = cause.getCause();
            check(cause instanceof IllegalStateException && "Native test failure".equals(cause.getMessage()),
                    "native passthrough preserves the original failure");
        }
        check(nativeCalls() == 1 && transport.notices.isEmpty() && transport.attempts == 0,
                "native failure is not swallowed, retried, or turned into a companion warning");
        reset(new long[] {258}); transport.active = false; choose(-1L, 2);
        transport.active = true; choose(-1L, 3);
        check(nativeCalls() == 1 && transport.sent.size() == 1, "server confirmation enables the companion after native passthrough");
        transport.active = false; choose(-1L, 4);
        check(nativeCalls() == 2 && transport.sent.size() == 1 && transport.notices.isEmpty(),
                "loss of server support restores stock behavior without duplicate requests");
        reset(new long[] {258}); transport.fail = true; choose(-1L, 2);
        check(transport.attempts == 1 && nativeCalls() == 0 && !transport.notices.isEmpty(), "send failure has no retry or native maximum fallback");
        reset(); choose(-1L, 2);
        check(transport.sent.isEmpty() && nativeCalls() == 0, "empty selection sends nothing");
        long[] hundred = new long[100]; for (int i = 0; i < hundred.length; i++) hundred[i] = i * 256L + 2L;
        reset(hundred); choose(-1L, Integer.MAX_VALUE); payload(Integer.MAX_VALUE, -1L, hundred);
        reset(new long[101]); choose(-1L, Integer.MAX_VALUE);
        check(transport.sent.isEmpty() && nativeCalls() == 0 && !transport.notices.isEmpty(), "oversized selection is bounded before any packet is sent");
        reset(new long[] {258, 514});
        inventoryClass.getMethod("plainDrag").invoke(source);
        check(inventoryClass.getField("nativeCalls").getInt(source) == 1 && transport.sent.isEmpty(), "ordinary drag never uses the Shift channel");
        connectionLifecycle();
        System.out.println("PASS: " + checks + " client quantity and hook checks");
    }
}
