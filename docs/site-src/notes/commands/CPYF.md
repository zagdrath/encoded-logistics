`CPYF` copies every record of one physical file into another. The two files needn't have the same format: each field
of the file copied to takes the value of the field **of the same name** in the file copied from, and fields with no
match are blank. Files with no field name in common are {{msg ELC2244}}. Done, it sends {{msg ELC2235}} with how many
records were copied.

- **`MBROPT(*ADD)`**, the default, adds the records to those already there; **`*REPLACE`** deletes those first.
- **`CRTFILE(*YES)`** makes `TOFILE` when it isn't there, with `FROMFILE`'s record format (key included) and text, in
  the library named or, without one, your current library. The records go in as they are, and `MBROPT` makes no
  difference. When `TOFILE` exists, `CRTFILE(*YES)` changes nothing and the copy goes ahead as usual. With the default
  `CRTFILE(*NO)`, a `TOFILE` that isn't there is {{msg ELC2205}}. A file made this way has no definition member (see
  {{cmd CHGPF}}).
- **Without a library**, either file is looked for down your library list.
- **All or nothing.** If any record can't go in, nothing is copied: {{msg ELC2243}} (a value that doesn't fit its field,
  with its row and the field), {{msg ELC2203}} (a duplicate `UNIQUE` key), {{msg ELC2208}} (more than
  {{config elcl.maxRecordsPerFile}} records) or {{msg ELC0207}} (no room on the drives). Text too long for its field is
  cut, not refused.
- Copying from an `ELSYS` system file takes the network's data as it is now, and needs the Firewall's view permission
  ({{msg ELC0401}}). Copying into a file needs `*CHANGE` on its library ({{msg ELC0401}}); nothing can be copied into
  `ELSYS` ({{msg ELC0205}}).

```elcl
/* A snapshot of the device list, made the first time and replaced each time after */
CPYF FROMFILE(ELSYS/DEVICES) TOFILE(ZAGLIB/DEVSNAP) MBROPT(*REPLACE) CRTFILE(*YES)
/* Add this run's low items to a running list with fewer fields */
CPYF FROMFILE(ZAGLIB/LOWNOW) TOFILE(ZAGLIB/LOWALL)
```

See [Copying, export and import](page:elcl/database-files#db-copy).
