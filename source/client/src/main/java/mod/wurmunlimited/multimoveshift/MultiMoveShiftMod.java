package mod.wurmunlimited.multimoveshift;

import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.*;
import javassist.*;
import org.gotti.wurmunlimited.modcomm.Channel;
import org.gotti.wurmunlimited.modcomm.IChannelListener;
import org.gotti.wurmunlimited.modcomm.ModComm;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.classhooks.InvocationHandlerFactory;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;

/** Forwards the existing Shift dialog's amount; ordinary drags are untouched. */
public final class MultiMoveShiftMod implements WurmClientMod, Initable, Configurable {
    private static final Logger LOG = Logger.getLogger("MultiMoveShift");
    private static final String INVENTORY = "com.wurmonline.client.renderer.gui.InventoryListComponent";
    private static final String SIGNATURE = "(Lcom/wurmonline/client/renderer/gui/InventoryListComponent$InventoryTreeListItem;JI)V";
    private boolean enabled = true;
    public String getVersion() { return "1.0.6"; }
    public void configure(Properties p) { enabled = Boolean.parseBoolean(p.getProperty("enabled", "true")); }
    public void init() {
        if (!enabled) return;
        final ChannelTransport transport = new ChannelTransport();
        try {
            install(HookManager.getInstance(), transport);
            transport.channel = ModComm.registerChannel("multimove.quantity.v1", new IChannelListener() {
                public void onServerConnected() {
                    if (transport.serverConnected()) LOG.info("[MultiMoveShift 1.0.6] server supports explicit Shift quantities");
                }
            });
            LOG.info("[MultiMoveShift 1.0.6] quantity hook installed; waiting for server support");
        } catch (Exception | LinkageError e) {
            LOG.log(Level.WARNING, "MultiMove Shift could not initialize; check the client and server versions.", e);
        }
    }

    interface Transport {
        boolean active();
        void send(ByteBuffer message);
        void notice(Object destination, String message);
    }

    static void install(HookManager manager, final Transport transport) throws Exception {
        CtClass inventory = manager.getClassPool().get(INVENTORY);
        if (inventory.isFrozen()) throw new IllegalStateException("Inventory was loaded before the Shift hook");
        CtMethod original = inventory.getDeclaredMethod("itemDroppedAmount", new CtClass[] {
                manager.getClassPool().get(INVENTORY + "$InventoryTreeListItem"), CtClass.longType, CtClass.intType});
        List<CtMethod> before = Arrays.asList(inventory.getDeclaredMethods());
        try {
            manager.registerHook(INVENTORY, "itemDroppedAmount", SIGNATURE, new InvocationHandlerFactory() {
                public InvocationHandler createInvocationHandler() { return quantityHandler(transport); }
            });
        } catch (RuntimeException | LinkageError e) {
            for (CtMethod method : inventory.getDeclaredMethods()) {
                if (!before.contains(method)) inventory.removeMethod(method);
            }
            original.setName("itemDroppedAmount");
            throw e;
        }
    }

    static InvocationHandler quantityHandler(final Transport transport) {
        return (destination, original, args) -> {
            // Without support on this connection, preserve the complete stock path.
            // Keep its invocation outside the companion's validation/error handling.
            if (!transport.active()) {
                try { return original.invoke(destination, args); }
                catch (InvocationTargetException e) { throw e.getCause(); }
            }
            try {
                int quantity = ((Integer)args[2]).intValue();
                if (quantity <= 0) {
                    transport.notice(destination, "MultiMove: enter a positive amount; nothing moved.");
                    return null;
                }
                Object source = method(args[0].getClass(), "getOwner").invoke(args[0]);
                long[] ids = (long[])method(source.getClass(), "getSelectedCommandTargets", int.class).invoke(source, quantity);
                if (ids.length == 0) return null;
                if (ids.length > 100) {
                    transport.notice(destination, "MultiMove: select at most 100 item IDs; nothing moved.");
                    return null;
                }
                ByteBuffer message = ByteBuffer.allocate(15 + ids.length * 8);
                message.put((byte)1).putInt(quantity).putShort((short)ids.length);
                for (long id : ids) message.putLong(id);
                message.putLong(((Long)args[1]).longValue());
                // Buffer.flip has the same descriptor on Java 8 and newer JDKs.
                ((java.nio.Buffer)message).flip();
                transport.send(message);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Shift transfer failed; not retrying or sending a maximum transfer.", e);
                transport.notice(destination, "MultiMove: quantity transfer failed; no automatic retry was sent.");
            }
            // Never also submit a stock request after attempting a supported transfer.
            return null;
        };
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Method method = c.getDeclaredMethod(name, parameters);
                method.setAccessible(true); return method;
            } catch (NoSuchMethodException missing) { }
        }
        throw new NoSuchMethodException(name);
    }

    private static Object currentConnection() {
        try { return method(ModComm.class, "getServerConnection").invoke(null); }
        catch (Exception | LinkageError unavailable) { return null; }
    }

    static final class ChannelTransport implements Transport {
        Channel channel;
        private final Supplier<Object> connection;
        private volatile Object supportedConnection;
        ChannelTransport() { this(MultiMoveShiftMod::currentConnection); }
        ChannelTransport(Supplier<Object> connection) { this.connection = connection; }
        private Object connection() {
            try { return connection.get(); }
            catch (RuntimeException | LinkageError unavailable) { return null; }
        }
        boolean serverConnected() {
            supportedConnection = connection();
            return supportedConnection != null;
        }
        public boolean active() {
            Object confirmed = supportedConnection;
            if (channel == null || !channel.isActive() || confirmed == null) return false;
            if (connection() == confirmed) return true;
            // Older ModComm versions retain channel IDs when the server changes.
            // A fresh acknowledgement must bind support to the new connection.
            if (supportedConnection == confirmed) supportedConnection = null;
            return false;
        }
        public void send(ByteBuffer message) { channel.sendMessage(message); }
        public void notice(Object destination, String message) {
            LOG.warning(message);
            try {
                Class<?> component = destination.getClass().getClassLoader().loadClass("com.wurmonline.client.renderer.gui.WurmComponent");
                Field field = component.getDeclaredField("hud"); field.setAccessible(true);
                Object hud = field.get(null);
                if (hud != null) {
                    method(hud.getClass(), "consoleOutput", String.class).invoke(hud, message);
                    method(hud.getClass(), "addOnscreenMessage", String.class, float.class, float.class, float.class, byte.class)
                            .invoke(hud, message, 1.0f, 0.7f, 0.2f, (byte)0);
                }
            } catch (Exception ignored) { /* The log still records the failure. */ }
        }
    }
}
