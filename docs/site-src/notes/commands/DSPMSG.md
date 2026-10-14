`DSPMSG` opens Display Messages, the screen for a message queue: what your jobs, the system and other users sent you
with {{cmd SNDMSG}}. It's also option 7 on the main menu.

{{layout 12_dspmsg}}

Messages are listed newest first, with their severity, who sent them (a user, a program or `QSYS`), when, and as much
of the text as fits. Unread messages are bright; showing them marks them read, and `MW` goes from the message line.

| Option or key | Does |
|---|---|
| `4` | Removes the message, straight away |
| `5` | Shows its details in a window: the whole text, its message ID, severity, sender and time |
| F5 | Refreshes the list |
| F11 | Removes every message in the queue, after you confirm with Enter (F12 backs out) |

## Another user's queue

`USR(*CURRENT)` (the default) or your own name shows your own queue. Any other name shows that user's queue, and
`USR(*SYSOPR)` (or `USR(QSYSOPR)`) the system operator's, where `SNDMSG TOUSR(*SYSOPR)` messages go. Looking at someone else's queue
needs one of:

- being a `*SECOFR`: the network's owner (the Firewall's), or a server operator;
- full authority on the system: a network with no Firewall, or the `SECLVL` system value at `10`
  (see [users and security](page:elcl/index#security)).

Otherwise the screen shows {{msg ELC0401}}. Messages you read or remove there are read or removed for that user.

```elcl nocompile="a command-line command: type it at the ===> prompt"
DSPMSG
DSPMSG USR(*SYSOPR)
DSPMSG USR(ZAGDRATH)
```

See [the Terminal OS screens](page:elcl/screens) and {{page elcl/jobs-and-scheduling}} for message queues.
