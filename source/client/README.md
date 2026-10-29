# MultiMove Shift 1.0.6 (optional client companion)

Makes the amount entered in the existing Shift-drag popup reach the MultiMove
server mod, including stored bulk rows. Normal automatic drags still work for
players without this companion.

The companion stays dormant until the current server confirms the matching
feature. On servers without it, Shift-drag uses Wurm's original behavior and
dialogs, without companion warnings. Switching servers requires fresh
confirmation; support from a previous connection is not reused.

To update from 1.0.5, close the client, replace
`mods/multimoveshift/multimoveshift.jar`, and restart. Existing MultiMove 1.0.5
servers remain compatible and do not need an update for this client fix.

1. Use MultiMove 1.0.5 or later on servers where exact Shift amounts are wanted.
2. Close the game client.
3. Copy this folder's `mods` into the client's **WurmLauncher** folder.
   The files are `mods/multimoveshift.properties` and
   `mods/multimoveshift/multimoveshift.jar`.
4. Remove the obsolete SelectedDrag client mod if present. Keep other mods.
5. Launch using the existing Client Mod Launcher and connect to the server.
   This build uses the client launcher 0.14 API.

These are client mod files; install the main package's root `mods` on the server.

On a supporting server, keep the game's Shift-drag option enabled. Hold Shift while dragging to an
inventory/container window, enter a positive amount, and submit. The server
uses that amount per selected bulk row; ordinary grouped rows use the stock
client's per-row ID limit. Wurm's normal availability, carry, slot, and container
rules still apply. No second server amount dialog is shown. Normal drags use
the server's configured automatic behavior.

The client log confirms support with:

```text
[MultiMoveShift 1.0.6] server supports explicit Shift quantities
```

If the matching server channel is unavailable or has not been confirmed for the
current connection, the original game method receives the original arguments.
The companion applies no custom quantity validation or messages on that path.

While active on a supporting server, select at most 100 underlying item IDs per
request. Zero and negative amounts move nothing. A failed custom submission
never causes a retry or a stock request that could transfer the maximum instead.
World-model drops keep the stock client behavior; this companion enhances the
existing inventory-window popup.

Without this companion, the stock client does not send the bulk amount entered
in its Shift popup. For a server-only setup, `autoQuantities=false` restores
Wurm's server quantity dialogs for bulk transfers instead.

See the main README and MIT LICENSE for source, building, tests, and limitations.
