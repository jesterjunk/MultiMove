package mod.wurmunlimited.multimove;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.logging.*;
import javassist.*;
import javassist.bytecode.CodeAttribute;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modcomm.IChannelListener;
import org.gotti.wurmunlimited.modcomm.ModComm;

/** Uses the IDs already supplied by the standard client's selection request. */
public final class MultiMoveMod implements WurmServerMod, Configurable, PreInitable, ServerStartedListener {
    private static final Logger LOG = Logger.getLogger("MultiMove");
    private boolean enabled = true;
    private boolean debug = true;
    private boolean autoQuantities = true;
    private boolean installed;
    public String getVersion() { return "1.0.6"; }
    public void configure(Properties p) {
        enabled = Boolean.parseBoolean(p.getProperty("enabled", "true"));
        debug = Boolean.parseBoolean(p.getProperty("debug", "true"));
        autoQuantities = Boolean.parseBoolean(p.getProperty("autoQuantities", "true"));
    }
    public void preInit() {
        if (!enabled) return;
        try {
            install(HookManager.getInstance().getClassPool(), debug, autoQuantities);
            installed = true;
            LOG.info("[MultiMove 1.0.6] native selection hooks installed; autoQuantities=" + autoQuantities);
        } catch (Exception | LinkageError e) {
            LOG.log(Level.WARNING, "MultiMove could not install; keeping native transfers.", e);
        }
    }

    public void onServerStarted() {
        if (!installed) return;
        try {
            registerQuantityChannel();
            LOG.info("[MultiMove 1.0.6] optional Shift quantity channel ready");
        } catch (Exception | LinkageError e) {
            LOG.log(Level.WARNING, "MultiMove transfers are active, but the optional Shift channel is unavailable.", e);
        }
    }

    // Register after startup so proxy signature resolution cannot load Player
    // while another mod is still patching game classes.
    static void registerQuantityChannel() {
        IChannelListener listener = (IChannelListener) Proxy.newProxyInstance(
                IChannelListener.class.getClassLoader(), new Class<?>[] {IChannelListener.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        if (method.getName().equals("equals")) return proxy == args[0];
                        return "MultiMove quantity listener";
                    }
                    if (method.getName().equals("handleMessage") && args != null && args.length == 2) {
                        try {
                            Object comm = args[0].getClass().getMethod("getCommunicator").invoke(args[0]);
                            comm.getClass().getMethod("multiMove$quantityRequest", ByteBuffer.class).invoke(comm, args[1]);
                        } catch (ReflectiveOperationException e) {
                            Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e;
                            LOG.log(Level.WARNING, "Stopped Shift transfer after an error; not retrying.", cause);
                        }
                    }
                    return null;
                });
        ModComm.registerChannel("multimove.quantity.v1", listener);
    }

    static void install(ClassPool pool, boolean debug) throws Exception {
        install(pool, debug, true);
    }

    static void install(ClassPool pool, boolean debug, boolean autoQuantities) throws Exception {
        CtClass comm = pool.get("com.wurmonline.server.creatures.Communicator");
        CtClass bulk = pool.get("com.wurmonline.server.questions.RemoveItemQuestion");
        CtClass question = pool.get("com.wurmonline.server.questions.Question");
        CtClass questions = pool.get("com.wurmonline.server.questions.Questions");
        CtClass stack = pool.get("com.wurmonline.server.behaviours.ActionStack");
        CtClass action = pool.get("com.wurmonline.server.behaviours.Action");
        for (CtClass type : new CtClass[] {comm, bulk, question, questions, stack, action}) {
            if (type.isFrozen()) throw new IllegalStateException(type.getName() + " was loaded too early");
        }
        CtMethod move = comm.getDeclaredMethod("reallyHandle_CMD_MOVE_INVENTORY",
                new CtClass[] {pool.get("java.nio.ByteBuffer")});
        CtMethod send = bulk.getDeclaredMethod("sendQuestion", new CtClass[0]);
        CtMethod answer = bulk.getDeclaredMethod("answer", new CtClass[] {pool.get("java.util.Properties")});
        CtMethod timeout = question.getDeclaredMethod("timedOut", new CtClass[0]);
        CtMethod remove = questions.getDeclaredMethod("removeQuestion", new CtClass[] {question});
        CtMethod poll = stack.getDeclaredMethod("poll", new CtClass[] {pool.get("com.wurmonline.server.creatures.Creature")});
        CtMethod stop = action.getDeclaredMethod("stop", new CtClass[] {CtClass.booleanType});
        Patch patch = new Patch();
        try {
            patch.method(comm, "private void multiMove$trace(String text) { if (" + debug
                    + ") java.util.logging.Logger.getLogger(\"MultiMove\").info(\"[MultiMove 1.0.6] \" + text); }");
            patch.field(comm, "private long[] multiMove$ids;");
            patch.field(comm, "private long multiMove$target;");
            patch.field(comm, "private int multiMove$next;");
            patch.field(comm, "private int multiMove$quantity;");
            patch.field(comm, "private boolean multiMove$inside;");
            patch.field(comm, "private boolean multiMove$waitingForAction;");
            patch.field(comm, "private boolean multiMove$automaticAnswer;");
            patch.field(comm, "private com.wurmonline.server.questions.Question multiMove$question;");
            for (String name : new String[] {"clear", "waiting", "cancel-question", "drain", "answered", "begin", "quantity-request", "auto-answer", "resume", "cancel-action"}) {
                patch.method(comm, resource(name));
            }
            patch.method(comm, "private boolean multiMove$shouldAutoQuantity() { return "
                    + (autoQuantities ? "true" : "this.multiMove$quantity > 0") + "; }");
            patch.method(comm, resource("auto-question"));
            patch.save(move);
            move.insertBefore("{ if (this.multiMove$begin($1, 0)) return; }");
            patch.save(send);
            send.insertAfter("{ if (this.getResponder() != null) this.getResponder().getCommunicator().multiMove$waiting(this); }");
            // Add the early return last so it bypasses the manual-dialog epilogue.
            send.insertBefore("{ if (this.getResponder() != null && this.getResponder().getCommunicator().multiMove$autoQuestion(this)) return; }");
            patch.save(answer);
            answer.insertAfter("{ if (this.getResponder() != null) this.getResponder().getCommunicator().multiMove$answered(this, $1); }");
            patch.save(timeout);
            timeout.insertBefore("{ if (this.getResponder() != null) this.getResponder().getCommunicator().multiMove$cancelQuestion(this); }");
            patch.save(remove);
            remove.insertBefore("{ if ($1 != null && $1.getResponder() != null) $1.getResponder().getCommunicator().multiMove$cancelQuestion($1); }");
            patch.save(poll);
            poll.insertAfter("{ if ($1 != null && $1.isPlayer()) $1.getCommunicator().multiMove$resume(); }");
            patch.save(stop);
            stop.insertBefore("{ if (this.getPerformer() != null && this.getPerformer().isPlayer()) this.getPerformer().getCommunicator().multiMove$cancelAction(); }");
        } catch (Exception | LinkageError e) {
            patch.rollback();
            throw e;
        }
    }

    private static String resource(String name) throws Exception {
        try (InputStream in = MultiMoveMod.class.getResourceAsStream("/" + name + ".java.txt")) {
            if (in == null) throw new IllegalStateException("Missing resource " + name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static final class Patch {
        final List<CtField> fields = new ArrayList<>();
        final List<CtMethod> methods = new ArrayList<>();
        final Map<CtMethod, CodeAttribute> original = new LinkedHashMap<>();
        void field(CtClass type, String source) throws Exception {
            CtField f = CtField.make(source, type);
            type.addField(f);
            fields.add(f);
        }
        void method(CtClass type, String source) throws Exception {
            CtMethod m = CtNewMethod.make(source, type);
            type.addMethod(m);
            methods.add(m);
        }
        void save(CtMethod m) {
            original.put(m, (CodeAttribute)m.getMethodInfo().getCodeAttribute()
                    .copy(m.getMethodInfo().getConstPool(), null));
        }
        void rollback() throws Exception {
            for (Map.Entry<CtMethod, CodeAttribute> entry : original.entrySet()) {
                entry.getKey().getMethodInfo().setCodeAttribute(entry.getValue());
            }
            for (int i = methods.size() - 1; i >= 0; i--) {
                CtMethod m = methods.get(i);
                m.getDeclaringClass().removeMethod(m);
            }
            for (int i = fields.size() - 1; i >= 0; i--) {
                CtField f = fields.get(i);
                f.getDeclaringClass().removeField(f);
            }
        }
    }
}
