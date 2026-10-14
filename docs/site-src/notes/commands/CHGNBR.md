Sets the cost a Router gives the hop to one of its neighbours, overriding the cost worked out from the link. Routes are
chosen by lowest total cost, so a higher cost steers traffic away from that neighbour and a lower one towards it. `RTR`
and `NBR` are as {{cmd RTVNBRSTS}} takes them ({{msg ELC2601}}, {{msg ELC2605}}).

`COST(n)` sets the override (1 to 1,000,000); `COST(*DFT)` removes it, so the worked-out cost applies again. The Router
works its paths out again with the new cost, which may change routes (a changed best path raises the `*RTECHG` trigger
event). On success the command sends {{msg ELC2609}}.

```elcl
CHGNBR RTR(ROUTER01) NBR(ELNET03) COST(5000)
CHGNBR RTR(ROUTER01) NBR(ELNET05) COST(*DFT)
```
