Switches an {{item optical_transport_shelf}}'s span onto its working or protect path by hand, for testing a protected
link or moving traffic off a path you're about to work on. `DEV` is the shelf's device name (any other device is
{{msg ELC2701}}) and `SPAN` its span's name (`OTS01-OTS02`, as {{cmd RTVOPTSTS}} and {{cmd DSPOPT}} show it;
{{msg ELC2703}} for any other).

The switch needs a {{item protection_card}} in the shelf and the path you pick to be up; otherwise the command fails with
{{msg ELC2705}}. Once switched, the shelf **holds** that path for as long as it stays up: a held protect path isn't
given back to a restored working path, as a revertive shelf otherwise would. When the held path goes down, the hold ends
and automatic protection switching takes over again. `PATH(*WORKING)` brings a span back from protect by hand. A switch
raises the `*PROTSWITCH` trigger event (reason `manual`).

The command sends no completion message. See {{page networking/optical}}.

```elcl
CHGPROT DEV(OTS01) SPAN('OTS01-OTS02') PATH(*PROTECT)
MONMSG MSGID(ELC2705) EXEC(SNDPGMMSG MSG('Protect path not available'))
```
