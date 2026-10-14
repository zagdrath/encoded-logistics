`SNDMSG` puts a message in a user's message queue on this system, where they read it with {{cmd DSPMSG}}. Messages
are kept until the user removes them, up to {{config elcl.messageCap default}} a queue (past that the oldest go). The
sender, shown in the queue's From column, is the user the command runs as.

`TOUSR` picks the queue:

| `TOUSR` | Goes to |
|---|---|
| `*REQUESTER` (default) | Your own queue: the user the command or job runs as |
| `*SYSOPR` | QSYSOPR, the system operator's queue |
| A user's name | That user's queue. The name isn't checked: a message to a name nobody signs on as waits in a queue of that name |
| `*ALL` | Every queue the system has, and QSYSOPR. A queue exists once a message has been sent to it, so a user who has never had a message (or removed them all with F11 on Display Messages) isn't reached |

A recipient who is signed on sees `MW` on the message line of their terminal. With
{{config elcl.messageChatNotice default}} on, a recipient who is in the game is also told in chat, with the system's
name and who sent it; a QSYSOPR message tells the network's owner (the Firewall's).

`TOTRM` is accepted and has no effect: every message goes to a user's queue, never to a terminal.

`SNDMSG` sends no completion message, and `MSG` is at most 64 characters.

```elcl
DCL VAR(&PCT) TYPE(*DEC) LEN(5 1)
RTVSTGSTS RTNPCT(&PCT)
SNDMSG MSG('Storage at' *BCAT %CHAR(&PCT) *TCAT '%') TOUSR(*SYSOPR)
SNDMSG MSG('Restock finished') TOUSR(ZAGDRATH)
SNDMSG MSG('Shutting down the east wing at 18:00') TOUSR(*ALL)
```

To send a message to the program's caller or job log rather than a user, use {{cmd SNDPGMMSG}}. See
{{page elcl/jobs-and-scheduling}} for message queues.
