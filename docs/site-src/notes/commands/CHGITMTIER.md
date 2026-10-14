Decides whether an item may be archived to tape. Storage has no per-item tier setting of its own, so the command works
through the lists every Tape Library keeps (the ones its panel shows): a **keep-hot** filter and a **pinned** list, nine
entries each. It needs at least one Tape Library serving the network ({{msg ELC1206}} without one) and works on items
only (fluids and gases never go to tape).

First it takes the item off every library's keep-hot and pinned lists, then:

| `TIER` | What it does |
|---|---|
| `*HOT` | Adds the item to the first library with a free keep-hot entry, and recalls all of it that's on tape now. Archiving leaves it alone from then on. |
| `*PIN` | Adds the item to the first free pinned entry. A pinned item is never archived, and a library that finds it on its tapes recalls it by itself. |
| `*COLD` | Queues what's hot of it to be archived by the first online library on its next archiving pass, whatever its age and however full hot storage is. The request isn't saved: a server restart before then forgets it. |
| `*AUTO` | Nothing more: the item is back under the library's normal archiving rules (age and hot-storage trigger). |

After `*COLD` the item isn't on any list, so the normal rules apply again: taking it out (a withdrawal, {{cmd MOVITM}})
recalls it. When every library's list is full, `*HOT` and `*PIN` fail with {{msg ELC1305}} (it names the device type,
`TAPELIB`). Because the item comes off every list first, a failed `*HOT` or `*PIN` leaves it on none.

The command sends no completion message. See {{page storage/tapes}} and {{page storage/hot-and-cold}}.

```elcl
CHGITMTIER ITEM(DIAMOND) TIER(*PIN)
CHGITMTIER ITEM(COBBLESTONE) TIER(*COLD)
MONMSG MSGID(ELC1206) EXEC(SNDPGMMSG MSG('No Tape Library on this network'))
```
