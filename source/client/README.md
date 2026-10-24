# MultiMove Shift 1.0.5 (optional client companion)

Makes the amount entered in the existing Shift-drag popup reach the MultiMove
server mod, including stored bulk rows. Normal automatic drags still work for
players without this companion.

1. Update the server to MultiMove 1.0.5 or later and restart it.
2. Close the game client.
3. Copy this folder's `mods` into the client's **WurmLauncher** folder.
   The files are `mods/multimoveshift.properties` and
   `mods/multimoveshift/multimoveshift.jar`.
4. Remove the obsolete SelectedDrag client mod if present. Keep other mods.
5. Launch using the existing Client Mod Launcher and connect to the server.
   This build uses the client launcher 0.14 API.

These are client mod files; install the main package's root `mods` on the server.

Keep the game's Shift-drag option enabled. Hold Shift while dragging to an
inventory/container window, enter a positive amount, and submit. The server
uses that amount per selected bulk row; ordinary grouped rows use the stock
client's per-row ID limit. Wurm's normal availability, carry, slot, and container
rules still apply. No second server amount dialog is shown. Normal drags use
the server's configured automatic behavior.

The client log confirms support with:

```text
[MultiMoveShift 1.0.5] server supports explicit Shift quantities
```

If the matching server channel is unavailable, the companion cancels the Shift
transfer and displays a local message. It never falls back to a maximum transfer
after an explicit request. Select at most 100 underlying item IDs per request.
Zero and negative amounts move nothing. World-model drops keep the stock
client behavior; this companion enhances the existing inventory-window popup.

Without this companion, the stock client does not send the bulk amount entered
in its Shift popup. For a server-only setup, `autoQuantities=false` restores
Wurm's server quantity dialogs for bulk transfers instead.

See the main README and MIT LICENSE for source, building, tests, and limitations.
