package mod.wurmunlimited.multimove;
import javassist.*;
import java.util.*;
public final class TestHarness {
    private static final String[] TYPES = {"com.wurmonline.server.creatures.Communicator",
            "com.wurmonline.server.questions.RemoveItemQuestion", "com.wurmonline.server.questions.Question",
            "com.wurmonline.server.questions.Questions", "com.wurmonline.server.behaviours.ActionStack",
            "com.wurmonline.server.behaviours.Action"};
    public static void main(String[] args) throws Throwable {
        if (args[0].equals("--api")) {
            ClassPool pool = new ClassPool(true);
            for (int i = 1; i < args.length; i++) pool.insertClassPath(args[i]);
            MultiMoveMod.install(pool, true);
            for (String name : TYPES) pool.get(name).toBytecode();
            Loader verify = loader(pool);
            for (String name : TYPES) verify.loadClass(name).getDeclaredMethods();
            System.out.println("PASS: server patch compiles against " + args[1]);
            System.out.println("PASS: patched native server classes load with JVM verification");
            return;
        }
        loader(new ClassPool(true)).run("mod.wurmunlimited.multimove.Scenarios", new String[] {"baseline"});
        ClassPool pool = new ClassPool(true);
        MultiMoveMod.install(pool, true, false);
        loader(pool).run("mod.wurmunlimited.multimove.Scenarios", new String[0]);
        loader(pool).run("mod.wurmunlimited.multimove.QuantityScenarios",
                args.length > 1 ? new String[] {"manual", args[1]} : new String[] {"manual"});
        ClassPool automatic = new ClassPool(true);
        MultiMoveMod.install(automatic, true, true);
        loader(automatic).run("mod.wurmunlimited.multimove.AutomaticScenarios", new String[0]);
        loader(automatic).run("mod.wurmunlimited.multimove.QuantityScenarios",
                args.length > 1 ? new String[] {"automatic", args[1]} : new String[] {"automatic"});
        ClassPool bad = new ClassPool(true);
        CtClass bulk = bad.get(TYPES[1]);
        bulk.removeMethod(bulk.getDeclaredMethod("answer"));
        // Preflight must leave everything untouched when a required method is missing.
        try { MultiMoveMod.install(bad, false); throw new AssertionError("Accepted incompatible server"); }
        catch (NotFoundException expected) { }
        assertClean(bad);
        bad = new ClassPool(true);
        CtClass comm = bad.get(TYPES[0]);
        CtMethod move = comm.getDeclaredMethod("reallyHandle_CMD_MOVE_INVENTORY");
        byte[] original = move.getMethodInfo().getCodeAttribute().getCode().clone();
        // Fail after fields and helpers have begun to install, exercising rollback.
        bad.get("com.wurmonline.server.items.Item").removeMethod(
                bad.get("com.wurmonline.server.items.Item").getDeclaredMethod("getParentOrNull"));
        try { MultiMoveMod.install(bad, false); throw new AssertionError("Accepted incompatible item API"); }
        catch (CannotCompileException expected) { }
        assertClean(bad);
        if (!Arrays.equals(original, move.getMethodInfo().getCodeAttribute().getCode())) throw new AssertionError("Changed native method on rollback");
        bad = new ClassPool(true);
        Map<String, byte[]> nativeCode = new LinkedHashMap<>();
        for (String name : TYPES) {
            for (CtMethod m : bad.get(name).getDeclaredMethods()) {
                if (m.getMethodInfo().getCodeAttribute() != null) {
                    nativeCode.put(m.getLongName(), m.getMethodInfo().getCodeAttribute().getCode().clone());
                }
            }
        }
        // Fail in the late action hook, after the move/question methods were changed.
        CtClass creature = bad.get("com.wurmonline.server.creatures.Creature");
        creature.removeMethod(creature.getDeclaredMethod("isPlayer"));
        try { MultiMoveMod.install(bad, false); throw new AssertionError("Accepted incompatible action owner"); }
        catch (CannotCompileException expected) { }
        assertClean(bad);
        for (String name : TYPES) {
            for (CtMethod m : bad.get(name).getDeclaredMethods()) {
                byte[] before = nativeCode.get(m.getLongName());
                if (before != null && !Arrays.equals(before, m.getMethodInfo().getCodeAttribute().getCode())) {
                    throw new AssertionError("Late rollback changed " + m.getLongName());
                }
            }
        }
        System.out.println("PASS: incompatible-server checks and full patch rollback");
    }
    private static Loader loader(ClassPool pool) {
        Loader loader = new Loader(pool);
        // Javassist 3.23 predates the JDK 17 reflection package used by this test runtime.
        loader.delegateLoadingOf("jdk.");
        return loader;
    }
    private static void assertClean(ClassPool pool) throws Exception {
        for (String name : TYPES) {
            CtClass type = pool.get(name);
            for (CtField f : type.getDeclaredFields()) if (f.getName().startsWith("multiMove$")) throw new AssertionError("Retained field");
            for (CtMethod m : type.getDeclaredMethods()) if (m.getName().startsWith("multiMove$")) throw new AssertionError("Retained helper");
        }
    }
}
