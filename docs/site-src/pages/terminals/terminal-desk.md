---
title: Terminal Desk and Swivel Chair
nav: Terminal Desk
section: terminals
order: 40
summary: The Terminal Desk, its CRT and drawer, and the Swivel Chair - where you sit down to use the Terminal OS.
---
The {{item terminal_desk}} is a steel-and-laminate desk with a green-screen CRT and a keyboard. It is the network's
command terminal: instead of a storage grid it runs the **Terminal OS**, where you type commands, look at jobs and
devices, and write ELCL programs. This page covers the desk as a block; the OS itself has its own section, starting at
{{page elcl/index}} and {{page elcl/using-the-terminal}}.

{{recipe terminal_desk}}

## Placing it {#placing}

The desk is two blocks wide and one deep, and faces you when you place it. The left half (seen from the front) holds the
CRT and keyboard: that's the part that matters. The right half is a drawer pedestal. Breaking either half breaks both,
and the desk drops its drawer's contents.

{{screenshot file="terminals/terminal-desk.png" alt="A Terminal Desk with its CRT lit green and a Swivel Chair in front of it" show="A Terminal Desk with its screen on, a dyed Swivel Chair pulled up to it, a network cable running into the back of the desk" angle="Standing behind and to one side of the chair, looking at the desk, at eye level" size="1600x900"}}

## On the network {#network}

The desk is a network device. Cables connect to it on any side except the front, and it uses one lane. It drains
{{config rack.terminalDeskDrain default}} FE/t, plus {{config rack.terminalDeskScreenDrain default}} FE/t while its
screen is on.

The screen follows the network:

- **Offline** (no network, no power or no lane): the screen is dark.
- **Coming online:** it boots for three seconds, with a degauss thump.
- **Online:** the screen stays lit and the CRT hums quietly. Keys clack as you type.

Each desk gets a device name the first time it is on a network, ELDESK01 and so on. You can rename it with
{{cmd RNMDEV}} or option 2 on Work with Devices.

## Using it {#using}

| Do | And you get |
|---|---|
| Use the CRT half | The Terminal OS screen |
| Use the pedestal half | The desk drawer: 9 slots |
| Sneak-use either half with an empty hand | Tidies the desk top, or clutters it again (just for looks) |

**The Terminal OS** needs the **View** permission on a network with a [Firewall](page:racks/security). It stays open
while you are near the desk and the desk is there. It is an 80x24 green screen with its own keys (F3 exits, F4 prompts,
F12 or <kbd>Esc</kbd> goes back) and command line. All of that is on {{page elcl/using-the-terminal}}.

**The drawer** is where things you withdraw from the Terminal OS to `*DRAWER` arrive, and where finished crafts can be
delivered. Hoppers and pipes can take items out of it through the pedestal half, so you can send withdrawals on
elsewhere.

**The screen colour** (green, amber or white) is the system's `PHOSPHOR` value, unless you pick your own with the
client option {{config terminal.phosphor default}} or the F24 (<kbd>Shift</kbd>+<kbd>F12</kbd>) page in the OS.

The console of an Integrated Midrange System opens the same Terminal OS session as a desk; see {{page crafting/midrange}}.

## The Swivel Chair {#swivel-chair}

The {{item swivel_chair}} is a five-star office chair to go with the desk. It does nothing for the network; it's for
sitting at the desk in style.

{{recipe-id encodedlogistics:swivel_chair}}

- **Placing:** it faces the way you are looking, at any angle, not only the four compass directions.
- **Sitting:** right-click it to sit down. The seat and back turn with you as you look around. One player at a time.
- **Getting up:** sneak. You stand up a block in front of the seat. Breaking the chair stands you up too.
- **Dyeing:** put it in a crafting grid with dyes to colour its upholstery, as you would leather armour. Undyed it's
  brown tweed. The colour stays when you break and replace it.

Behind the scenes you sit on an invisible seat entity (`encodedlogistics:seat`) that the chair makes for you. It goes
away as soon as you get up or the chair is gone, and it can't be summoned on its own.
