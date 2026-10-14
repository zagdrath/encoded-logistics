`DSPOPT` opens **Display Optical Status**: the network's optical gear ({{item optical_multiplexer}}s,
{{item optical_amplifier}}s and {{item optical_transport_shelf}}s) and their spans. Each span shows its path, length,
the lanes it delivers, the lanes lost, signal quality (100% less 4% for each lane lost) and its 1+1 protection; under
them, the last protection switch. The top line names the network and counts the shelves, multiplexers and amplifiers.
You type it at a command line; it doesn't run in a program.

The screen refreshes every two seconds; PageUp / PageDown roll it. **F10** shows the channels instead (each device's
channels, the network on each, the far end, lanes and whether it's up); F10 again goes back. Enter goes back.

In a program, {{cmd RTVOPTSTS}} reads the same figures, and {{cmd CHGPROT}} switches a protected span's path. See
{{page networking/optical}} and {{page elcl/screens}}.

{{layout 29_dspopt}}

```elcl nocompile="a command-line command: type it at the ===> prompt"
DSPOPT
```
