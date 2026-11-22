# DonutRTPPlaytime

Playtime requirements for **DonutRTP**: by default **5 hours for the Nether** and **24 hours for the End**.

It is a small companion plugin. DonutRTP itself is not modified, so DonutRTP updates won't break it.

## How it works
- **Final gate:** DonutRTP fires a cancellable `RtpPreTeleportEvent` before every teleport (menu, command and RTP zones).
  If the destination world needs more playtime than the player has, the event is cancelled. Nothing is charged and no
  cooldown starts.
- **Early gate:** `/rtp nether`, `/rtp end` and clicking the menu item are refused immediately, before the warmup.
- **Menu lore:** a locked destination shows `Locked: needs 5 hours of playtime` and how long is left.
- **Playtime source:** with `source: AUTO` it uses **UltimateDonutSmp's playtime** (the same number as `/playtime`,
  and the one UltimateDonutSmp's own RTP checks). If UltimateDonutSmp isn't there it falls back to the vanilla
  "time played" statistic.

## Install
1. Put the DonutRTP jar and this plugin's jar in `plugins/`. Restart (a reload won't register the hook).
2. Edit `plugins/DonutRTPPlaytime/config.yml` if you want other hours, then `/rtpplaytime reload`.

## Config
```yaml
requirements:
  NETHER: 5      # any world whose environment is NETHER
  THE_END: 24    # any world whose environment is THE_END
  # world_nether: 5   <- a world name overrides the environment
```
Other options: `playtime.source`, `exempt-sources` (ADMIN by default, so `/rtp player <name>` still works), `bypass-permission`,
`early-check`, the refusal message, the deny sound and the menu lore.

## Commands / permissions
- `/rtpplaytime reload` - reload config
- `/rtpplaytime check [player]` - show someone's playtime and which destinations they have unlocked
- `donutrtp.playtime.bypass` (ops by default) - skip every requirement
- `rtpplaytime.admin` (ops by default) - use the command

## Build
`mvn -B package` (Java 21), or push to GitHub and download the **DonutRTPPlaytime** artifact from the Build workflow.
Only the Paper API is needed. DonutRTP and UltimateDonutSmp are read by reflection.

## Test
1. `/rtpplaytime check` shows your hours (use a non-op account, because ops bypass).
2. Temporarily set `NETHER: 9999`, `/rtpplaytime reload`, then `/rtp nether` should be refused right away.
3. Open `/rtp`: the Nether item shows the locked lines.

## Notes
- UltimateDonutSmp has its own RTP with the same idea in its `rtp.yml`
  (`REQUIRED-PLAYTIME-HOURS` per world; defaults are Nether 5.0 and End 10.0). If players also use that RTP, set its End value
  to 24 there too.
- Ops have `donutrtp.playtime.bypass`, so test with a normal account.
