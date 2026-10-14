Reports on an optical span: the fiber run between two optical devices, as {{cmd DSPOPT}} shows it. `DEV` is an
{{item optical_multiplexer}} (block or rack), an {{item optical_amplifier}} (block or rack) or an
{{item optical_transport_shelf}}, by its device name; anything else is {{msg ELC2701}}. See {{page networking/optical}}.

## Picking the span

- A multiplexer has one span, named after both ends: `MUX01-MUX02` (`MUX01-?` before it's paired).
- A Transport Shelf's span is named the same way (`OTS01-OTS02`) and has a working path and, with a {{item protection_card}}, a
  protect path.
- An amplifier's span is its own name: the figures are the amplifier's (full lanes, 100% quality while it's on).

`SPAN(*FIRST)` (the default) takes the active span (on a shelf, the path in use). A name takes that span, preferring the
active path when both a shelf's paths have it. A name the device doesn't have is {{msg ELC2703}}.

`CHANNEL(n)` (1 to 40) makes `RTNLANES` that channel's lanes instead of the whole span's. A channel with no network on it
is {{msg ELC2704}}.

## Returns

| Variable | Gets |
|---|---|
| `RTNLANES` | Lanes the span (or the channel) delivers; `0` while it's down |
| `RTNLOSS` | Lanes lost to span length (see {{config optical.fiberSpanThreshold}}); `0` while it's down |
| `RTNQUAL` | Signal quality, 0 to 100; `0` while it's down |
| `RTNPROT` | The path's protection state: `*ACTIVE`, `*STANDBY`, `*FAILED`, or `*NONE` without protection |

The command sends no completion message.

```elcl
DCL VAR(&QUAL) TYPE(*INT)
DCL VAR(&PROT) TYPE(*CHAR) LEN(8)
RTVOPTSTS DEV(OTS01) RTNQUAL(&QUAL) RTNPROT(&PROT)
IF COND(&QUAL *LT 80) THEN(SNDMSG MSG('OTS01 span quality' *BCAT %CHAR(&QUAL) *TCAT '%') TOUSR(*SYSOPR))
```
