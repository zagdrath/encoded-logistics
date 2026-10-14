`CLRDSP` clears a Display Panel screen, or one region of it.

- `RGN(*ALL)` (the default) clears the whole screen: every text line, every region and every image. The screen is left
  with the single region `A` over all of it, showing nothing, and its next {{cmd SNDDSPTXT}} `LINE(*NEXT)` goes at the
  top. Its mode (Text or Dashboard) and its own background stay as they were.
- `RGN(name)` takes that region's widget away (it shows `*NONE`), keeping the region's position, size and background.
  A region the screen doesn't have is {{msg ELC1314}}. On a screen with no regions set up, `A` is the whole screen.

To take the text lines away and nothing else, write a line with `CLEAR(*YES)` on {{cmd SNDDSPTXT}}. To remove one
region altogether, clear the screen and set the layout up again with {{cmd CHGDSPRGN}}.

The screen doesn't have to be online. A name that isn't on the network is {{msg ELC1301}}, and a device that isn't a
Display Panel screen {{msg ELC1303}}. `CLRDSP` sends no completion message.

```elcl
CLRDSP DEV(DSP01)              /* everything */
CLRDSP DEV(DSP01) RGN(B)       /* just region B's widget */
```

See [Regions](page:control/display-panels#regions).
