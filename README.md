These mods were developed with the aid of ChatGPT-6.1 Sol Max

https://chatgpt.com/share/6adc033f-e7a4-83ea-b9c2-cece90d7d488

---

### MultiMove - Wurm Unlimited Server Mod

### with optional MultiMoveShift Client Mod

---

Move single items or highlighted selections between containers and inventory without quantity prompts, respecting available space and carry limits. Works for all players through the server, with an optional client companion for choosing specific quantities using Shift-drag.

---

<br>
<br>

# MultiMove 1.0.5

Move highlighted inventory rows with one drag using the standard Wurm Unlimited
client. Install the root `mods` folder on the server. Normal single and multiple
drags work for players without a client mod.

**Exact amounts entered in the Shift-drag popup require the included optional
client companion.** Version 1.0.5 adds this support. The stock client's Shift
popup limits how many ordinary item IDs it sends per selected row, but a stored
bulk row is represented by one ID regardless of the amount entered. Its move
packet contains neither that bulk amount nor a Shift flag. The server alone
cannot distinguish those requests from a normal drag.

Automatic quantities apply to normal single-row drags and multi-selection. Bulk
withdrawals use Wurm's current free inventory slots and carry limits, leaving
anything that will not fit in the source. Timed work still finishes before
the next row is attempted.

The supplied client already sends the highlighted item IDs for normal inventory
and world-container drops. Inspection of the supplied server found a different
problem: some native move paths return from the entire request after processing
one entry. In particular, withdrawing a stored bulk row opens its quantity
question and returns without processing the remaining selected rows.

This mod plans the selection, then calls Wurm's original move handler once per
planned item ID. A rejection of one item does not discard the following items.
Every individual move still goes through native permission, distance, weight,
capacity, and item restrictions. An unexpected native exception stops the
remaining transfer without retrying anything.

**Normal single and multiple bulk-row drags use the native maximum without showing
a dialog.** This means all available items or as many as Wurm allows you to
transfer, subject to its normal carry, capacity, and storage rules. It does not
guarantee that every large stack will be emptied. Ordinary single items also
continue through the original move handler.

When withdrawing to player inventory, Wurm recalculates its available item slots
for each bulk row. For example, with three free slots, dragging a bulk row of
50 items withdraws up to three and leaves the rest stored. Later rows use the
slots still available after earlier moves. If an action has to wait, the
calculation happens when the withdrawal runs, using the space available then.
Native inventory counting, permissions, carry weight, and destination checks
remain in charge; this mod does not keep a separate slot count.

Timed bulk actions run one at a time through the existing action stack. If the
player is already busy, the next quantity answer waits until that slow work
finishes. Stopping a native action abandons the remaining selected work. No
background worker or repeated transfer attempt is used.

Set `autoQuantities=false` in the properties to restore the previous sequence
of quantity dialogs. With that option, answering a positive quantity continues
the selection; None, closing, or replacing the question abandons the rest.
This is the server-only way to choose bulk amounts: use Wurm's server quantity
dialog. It also restores those dialogs for ordinary bulk drags. The amount
entered in a stock client's first Shift popup still is not transmitted for bulk.

With the companion installed, the existing Shift popup sends the entered
positive amount through a negotiated ModComm channel. The server supplies that
amount to each selected bulk row's native answer instead of the maximum.
Ordinary grouped rows retain the client's per-row ID limit. Native availability,
weight, inventory slots, permissions, and destination checks still apply.
No second server quantity dialog is shown, including with `autoQuantities=false`.

## Upgrade from MultiMove 1.0.2, 1.0.3, or 1.0.4

Stop the server, replace `mods/multimove/multimove.jar` with the supplied JAR,
and restart. Keep `autoQuantities=true` for transfers without quantity dialogs.
For exact Shift amounts, also install the optional companion as described below.
Players who only need normal automatic transfers can keep using the stock client.

## Upgrade from SelectedDrag Server

1. Stop the server.
2. Remove `mods/selecteddragserver.properties` and the `mods/selecteddragserver/`
   folder. Install only one version of the mod.
3. Copy this package's `mods` folder into **WurmServerLauncher**. The new files
   are `mods/multimove.properties` and `mods/multimove/multimove.jar`.
4. Copy any preferred `enabled`, `debug`, and `autoQuantities` values from your
   previous configuration into `multimove.properties`, then restart the server.

`autoQuantities=true` is the default for normal automatic transfers.

## Installation

1. Stop the server and close the game client.
2. If the old **client** SelectedDrag package is still in the server's `mods`
   folder, remove `mods/selecteddrag.properties` and `mods/selecteddrag/` there.
   That package implements `WurmClientMod` and cannot load on the server.
3. Copy this package's `mods` folder into the **WurmServerLauncher** folder,
   merging it with the existing folder. The installed files are:

   - `WurmServerLauncher/mods/multimove.properties`
   - `WurmServerLauncher/mods/multimove/multimove.jar`

4. Remove the earlier client SelectedDrag files from `WurmLauncher/mods`:
   `selecteddrag.properties` and the `selecteddrag` folder. Keep other mods.
5. Start the server with ago1024's existing Server Mod Launcher, then start the
   client normally. The build targets your Mod Loader 0.47 installation.

The server startup log should include:

```text
[MultiMove 1.0.5] native selection hooks installed; autoQuantities=true
[MultiMove 1.0.5] optional Shift quantity channel ready
```

No replacement game JAR, database changes, or launcher update is included.

## Optional client companion for exact Shift amounts

Install this only for players who want the Shift popup's bulk amount honored.
The server must run MultiMove 1.0.5 or later with the feature enabled.

| Package folder | Copy into | Purpose |
| --- | --- | --- |
| `mods` | `WurmServerLauncher` | Server mod for all players |
| `optional-client/mods` | `WurmLauncher` | Optional Shift amount forwarding for that player |

Close the client, copy `optional-client/mods` into its `WurmLauncher` folder,
then launch through your existing Client Mod Launcher. The client files are
`mods/multimoveshift.properties` and `mods/multimoveshift/multimoveshift.jar`.
They use the client launcher interface and belong in the client installation.
The companion uses the 0.14 client mod launcher API. Keep the stock client's
Shift-drag option enabled. Remove the obsolete SelectedDrag client mod if it
is still installed; keep other client mods.

On a supporting server the client log includes:

```text
[MultiMoveShift 1.0.5] server supports explicit Shift quantities
```

Hold Shift while dropping into an inventory or container window, enter an amount,
and submit. The value applies per selected row, matching the stock popup's
ordinary-stack behavior. For example, with 20 stored items, entering 3 asks Wurm
to transfer 3 rather than its automatic maximum. Normal drags still transfer
automatically. Zero or negative amounts move nothing.

The companion cancels an explicit transfer if the server channel is unavailable
or more than 100 IDs are selected. It displays a local message rather than
falling back to an automatic maximum. It does not retry a failed submission.
World-model drops keep their existing client behavior; the stock Shift popup
being enhanced here belongs to inventory/container windows.

## Use

Select one row, or highlight several using normal Ctrl-click or Shift-click.
Release Ctrl/Shift and drag one of the highlighted rows into an open destination
container or onto a container that Wurm normally accepts as a move destination.
The native client must include those IDs in the request; a server cannot recover
highlights omitted by another client or client mod.

- Ordinary selected items are attempted once each, in selection order.
- Grouped item IDs are deduplicated.
- Drops into the player inventory window and onto virtual inventory groups
  resolve to Wurm's actual destination. Dropping onto an ordinary item uses
  its nearest containing inventory or bag.
- Selecting a container and its contents moves the container once with its
  contents, rather than pulling its children out afterward.
- Dropping into the selection itself, or inside a selected container, cancels
  the selection before any item is moved.
- Missing items and items already directly inside the destination are skipped.
- A later drag cancels any remaining selection and invalidates its pending
  bulk quantity question. A native action already running is left to Wurm; use
  the normal Stop control to cancel that action too.
- Normal single-ID and multi-ID container requests use automatic quantities
  when `autoQuantities=true`. Exact Shift quantities use the optional companion;
  stock bulk move packets do not contain the amount chosen in the client popup.
- Trade and bank destinations, targets without a containing inventory or
  container, and oversized packets retain native packet handling.
- Wurm's native limit of **100 IDs per request** remains. Use selections of at
  most 100 underlying items for this enhancement, including grouped rows.

Transfers can partly succeed. A rejected item remains where Wurm leaves it;
there is no promise of an atomic all-or-nothing move. Native Event-tab messages
explain individual rejections. Normal bulk drags use the native maximum rules
described above; companion Shift requests use the entered amount.

## First in-game check

Start with three different ordinary items in a backpack or wooden chest and a
destination with room. Highlight all three, release the selection keys, and
drag one highlighted item to the destination. Check the resulting contents and
the Event tab.

Then drag one bulk row, followed by two different bulk rows together. Neither
test should show a quantity dialog with `autoQuantities=true`. For a bulk-to-bulk
transfer, allow the native timer to finish for each type.

With three free inventory slots, drag a stored row containing more than three
items into the empty space in the inventory window. Check that at most three
are withdrawn, the remainder stay stored, and no quantity dialog appears.
Repeat onto an existing inventory item and an inventory group, then with
several different rows selected; they should share the available slots.
Also check Stop during
a timed multi-selection, one ordinary item, and a full destination. Native
Event-tab messages still apply when weight or other restrictions prevent a move.

With the optional companion installed, Shift-drag a bulk row of 20 and enter 3.
Check both a container destination and player inventory: 3 should be requested,
with 17 remaining when the native move succeeds. Repeat with multiple selected
rows, a timed bulk-to-bulk action, a nearly full inventory, and then a normal
drag. The normal drag should use the maximum again. With three free slots and
an entered amount of 2, at most two should be withdrawn.

Debug logging is enabled for this test build. The server log records
`received selection; planned=N`, one `native move item=...` for each attempted
ID, and `using native maximum quantity` for automatic bulk answers. These
indicate attempts, not successful transfers. After confirming behavior, set `debug=false`
in `mods/multimove.properties` and restart the server.
Explicit requests log `using requested quantity=N`.

## Validation

The JAR is compiled for Java 8 and uses the server mod launcher interface.
The actual patches compiled against the uploaded `server(1).jar`, and all six
transformed native server classes loaded with JVM bytecode verification enabled.

83 behavioral checks pass on small transformed server fixtures. They cover
14-item transfers, mixed ordinary and bulk rows, sequential quantity questions,
native question removal order, rejection, capacity, stale entries, duplicate IDs,
selected ancestors, self/descendant destinations, cancellation, new drags,
disconnect/death, independent players, and native exceptions without retries.
The unpatched fixture reproduces the inspected first-bulk-row early return.
437 additional automatic-quantity checks cover 100 bulk rows without dialogs,
the native maximum sentinel, single-row automatic quantities, unrelated native
questions, partial and full inventories, mixed selections, ordinary item slots,
slot changes while waiting, native transfer limits, serial timed actions,
busy players, Stop, replacement drags, disconnect/death, expired deferred
questions, and independent players. Inventory checks include the native `-1`
destination, a real inventory ID, virtual groups, existing item rows, and a
carried bag. The quantity-popup regressions failed before the destination fix
and pass with it.
Separate tests check incompatible APIs and rollback of a failed patch, including
a late failure after the move/question methods were already modified.

78 explicit-quantity/channel checks pass with `autoQuantities=true`, and 78
with it disabled. They cover actual ModComm listener registration, entered
amounts, inventory aliases, live slots, mixed rows, sequential native actions,
cancellation, normal drags after explicit amounts, invalid messages, player
isolation, and a packet produced by the client companion's real hook.
134 client checks cover the real mod loader hook, bulk quantities, native
ordinary-stack ID clipping, normal drags, invalid amounts, an unavailable server,
bounded selections, send failures without retries, and rollback of a failed hook.
The client hook also compiles against the uploaded `client-patched.jar`, and
the patched native client class loads with JVM bytecode verification enabled.

These checks do not launch a game world or establish compatibility with every
other server mod. The server behavior in the former SelectedDrag Server 1.0.0 was confirmed
working by the user. Automatic quantities were introduced in 1.0.1 and extended
to single-row drags in 1.0.3. Destination handling was extended in 1.0.4.
This 1.0.5 client/server quantity feature still needs in-game confirmation.

## Disable or remove

Stop the server, set `enabled=false` in the mod properties or remove the two
installed mod files, and restart. No saved world changes need to be undone.
To remove the optional companion, close the client and remove its
`multimoveshift.properties` and `multimoveshift` folder.

## Source and rebuilding

`source/` contains the original mod source and tests under the MIT license.
No proprietary game classes or third-party libraries are bundled in the mod
JAR or source archive. With Python 3.8+ and Java 8+ available, run from `source/`:

```sh
python3 client/build.py --test --api /path/to/client-patched.jar
python3 build.py --test --quantity-packet client/build/shift-request.bin --api /path/to/server.jar
python3 package.py
```

If your game uses a separate common JAR, supply it after `server.jar` in the
`--api` arguments. The build downloads checksum-pinned compile dependencies.
The ordinary behavioral tests also run without any game JAR:

```sh
python3 build.py --test
python3 client/build.py --test
```

The mod patches `Communicator.reallyHandle_CMD_MOVE_INVENTORY`,
`RemoveItemQuestion.sendQuestion/answer`, `Question.timedOut`, and
`Questions.removeQuestion`, `ActionStack.poll`, and `Action.stop` during server
pre-initialization. Failed installation
rolls back the modified methods and injected members. Pending selection state
belongs to the player's communicator. Continuation uses Wurm's existing action
poll, with no worker threads, new recurring poller, or global player queue.
Automatic quantity answers are limited to single-ID and multi-ID drags handled
by this mod; independent quantity questions retain their native behavior.
The companion wraps only `InventoryListComponent.itemDroppedAmount`, which the
existing Shift popup calls on submission. It uses the mod loader's negotiated
`multimove.quantity.v1` channel. The server handles that message on the same
communicator dispatch path as ordinary moves, validates its bounded payload,
and delegates every item to the original native move handler.
