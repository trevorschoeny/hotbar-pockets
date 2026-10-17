# Hotbar Pockets

Hotbar Pockets adds up to three extra slots, called pockets, behind each hotbar slot. A key cycles the hotbar slot through its pockets, so one hotbar slot can hold a pickaxe, a shovel, an axe, and a second pickaxe without using any room in your inventory.

It has to be installed on the server as well as the client. In singleplayer, your game is both.

## Features

### Pockets

- Hover a hotbar slot in your inventory to see its pockets in a row above it.
- Give any hotbar slot up to three pockets with the plus button, and take them away with the minus button.
- Each world has its own layout of pockets.
- Shift-clicks, double-click collecting, and drag filling never put items into a pocket.

### Cycling

- Press the Right or Left arrow to cycle the selected hotbar slot through its pockets.
- With your inventory open, the keys cycle the column under your mouse.

### Mini hotbar

- A small hotbar beside your hotbar shows what's in the cycle and slides with each press.
- It can be turned off.

### With Inventory Plus

- When a tool in your hand breaks, Restock can cycle a spare from that slot's pockets into your hand.
- Inventory Plus's locks and Reach work on pockets, so you decide which moves may use them.
- The settings are on a Pockets tab in Inventory Plus's settings menu.

## Requirements

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [MenuKit](https://modrinth.com/mod/menukit) 7.1.0 or newer, below 8.0.0, which draws the pockets, the plus and minus buttons, and the mini hotbar.
- [SlotKit](https://modrinth.com/mod/slotkit) 1.1.0 or newer, below 2.0.0, which adds the pocket slots to your inventory, saves them with your player on the server, and checks that a joining player has the mod.

## Pairs well with

- [Inventory Plus](https://modrinth.com/mod/inventory-plus-trevlar) 3.0.0 or newer, my mod for restocking, sorting, and locking slots. With it, Restock and Auto Tool Switch can take from your pockets, its locks and Reach work on pockets, and Hotbar Pockets gets a settings tab. Older versions can't be installed alongside it.

## Compatibility

It runs on Minecraft 26.3 with Fabric. Install it on both the client and the server. A server with Hotbar Pockets turns away players who don't have it, with a message naming the mods to install.

Items in pockets don't mend from XP.

Hotbar Pockets used to be Inventory Max. Anything left in Inventory Max's pockets, elytra slot, or totem slot is put back in your inventory the first time you join a world, and whatever doesn't fit drops at your feet.

## How to use

### Adding pockets

Open your inventory and hover a hotbar slot. Two small buttons appear just under it, minus on the left and plus on the right.

- Click plus to add a pocket. A hotbar slot can have up to three. The pockets sit in a row above the hotbar slot, on a raised box.
- Click minus to remove the rightmost pocket. Its item goes into your inventory, or drops at your feet if there's no room.
- Minus is grayed when the slot has no pockets, and plus is grayed when it has three.
- The row stays open while your mouse is on the hotbar slot, its buttons, its pockets, or the space between them.
- Pockets show in your inventory and in the creative inventory's Survival Inventory tab. They don't show while a chest or other container is open, and their items stay in them.

Each world, and each server, has its own number of pockets for each hotbar slot, and a new world starts with none. The number of pockets is saved on your computer. The items are saved with your player on the server.

### Using pockets

A pocket holds items like any slot. Click, drag, and place items in it as usual.

- Shift-clicking an item in your inventory never sends it into a pocket. Double-click collecting and drag filling skip pockets too.
- Shift-click a pocket to move its item into your inventory.
- When you die, items in pockets drop with the rest of your inventory, and they're kept when the keepInventory game rule is on.

### Cycling

A hotbar slot and its pockets make a cycle.

- Press the Right arrow to bring the leftmost pocket's item into your hand. Every other pocket's item moves one place left, and the item you were holding goes into the rightmost pocket.
- Press the Left arrow to go the other way. The rightmost pocket's item comes into your hand, and the item you were holding goes into the leftmost pocket.
- With no screen open, the keys cycle the selected hotbar slot.
- In your inventory, hover a hotbar slot or its pockets and press a key to cycle that column. In creative, use the keys with no screen open.
- A hotbar slot with no pockets doesn't cycle.

Both keys are in Minecraft's Controls, under Hotbar Pockets.

| Key | Default |
|---|---|
| Pocket Cycle Forward | Right arrow |
| Pocket Cycle Backward | Left arrow |

### Mini hotbar

While the selected hotbar slot has pockets, a small hotbar beside your hotbar shows the item in your hand, then each pocket's item from left to right. That's the order the Right arrow brings them to your hand. It slides with each press.

With Inventory Plus installed, Beside the hotbar on the Pockets tab turns it off.

### With Inventory Plus

- **Restock.** When a tool in your hand breaks and a spare is in one of that hotbar slot's pockets, Restock cycles the spare into your hand.
- **Auto Tool Switch.** It can also use tools in your pockets when it picks a tool for the block you're mining.
- **Locks.** Press L over a pocket to lock it, the same as any slot.
- **Reach.** The pockets show as one checkbox, Pockets, under each move that can use them. Clearing Pockets under Restock takes from stops Restock from using your pockets.
- **Pocket Cycler.** Cycling is listed in Reach as Pocket Cycler, under Other mods. Locks don't stop it by default. Clearing Slot lock under Pocket Cycler stops a slot from cycling while any slot in its cycle is locked.

### Settings

The settings are on the Pockets tab in Inventory Plus's settings menu. Ctrl-click (Cmd on a Mac) the plus or minus button to open it there. Without Inventory Plus there's no settings menu, and the defaults apply.

| Setting on the Pockets tab | Default |
|---|---|
| Pockets (the switch) | On |
| Show the Pockets button | Off |
| Beside the hotbar | Mini hotbar |
| Pocket Cycle Forward | Right arrow |
| Pocket Cycle Backward | Left arrow |

- Turning Pockets off hides every pocket, the plus and minus buttons, and the mini hotbar, and the keys stop working. Items stay in their pockets, and Restock and Auto Tool Switch leave them alone until Pockets is back on.
- Show the Pockets button adds a Pockets switch to the column on the right of your inventory. Left-click it to turn Pockets on or off, or Ctrl-click it to open the tab.
- Reset to Defaults asks first, then puts every setting and both keys on the tab back. Inventory Plus's Reset everything resets them too.

## License

MIT.
