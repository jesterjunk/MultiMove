package mod.wurmunlimited.multimoveshift;

import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import javassist.*;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;

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
    public static void main(String[] args) throws Exception {
        java.util.logging.Logger.getLogger("MultiMoveShift").setUseParentHandlers(false);
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
        check(transport.sent.isEmpty() && nativeCalls() == 0 && !transport.notices.isEmpty(), "unsupported server cancels rather than silently transferring maximum");
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
        System.out.println("PASS: " + checks + " client quantity and hook checks");
    }
}
