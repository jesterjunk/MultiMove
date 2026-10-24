package com.wurmonline.server.creatures;
import java.util.*;
import java.nio.ByteBuffer;
import com.wurmonline.server.*;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.questions.RemoveItemQuestion;

/** Small fixture of the native packet shape and whole-request early return. */
public class Communicator {
    private final Player player;
    public final List<Long> attempts = new ArrayList<>();
    public final List<Long> moves = new ArrayList<>();
    public final List<String> messages = new ArrayList<>();
    public final List<RemoveItemQuestion> dialogs = new ArrayList<>();
    public int nativeCalls;
    public Communicator(Player p) { player = p; p.setCommunicator(this); }
    public void receive(ByteBuffer bytes) throws Exception { reallyHandle_CMD_MOVE_INVENTORY(bytes); }
    public void sendNormalServerMessage(String message) { messages.add(message); }
    private void reallyHandle_CMD_MOVE_INVENTORY(ByteBuffer bytes) throws Exception {
        nativeCalls++;
        int count = bytes.getShort() & 65535;
        long[] ids = new long[count];
        for (int i = 0; i < count; i++) ids[i] = bytes.getLong();
        long target = bytes.getLong();
        // Native requests use -1 for player inventory and unwrap virtual groups.
        if (target == -1L) target = player.getInventory().getWurmId();
        Item alias = Items.getItem(target);
        if (alias.getTemplateId() == 1392) target = alias.getParentId();
        for (int i = 0; i < count && i < 100; i++) {
            if (!player.hasLink() || player.isDead() || player.isUndead()) return;
            Item item = Items.getItem(ids[i]);
            Item destination = Items.getItem(target);
            Set<Long> visited = new HashSet<>();
            while (!destination.isHollow() && !destination.isBulkContainer()) {
                if (!visited.add(destination.id) || destination.getParentOrNull() == null) break;
                destination = destination.getParentOrNull();
            }
            attempts.add(item.id);
            if (item.fail) throw new java.io.IOException("Native failure");
            if (item.rejected || destination.contained >= destination.capacity) {
                sendNormalServerMessage("Native move rejected");
                return;
            }
            if (item.bulk) {
                new RemoveItemQuestion(player, item, destination).sendQuestion();
                return;
            }
            moves.add(item.id);
            item.parent = destination.id;
            destination.contained++;
        }
    }
}
