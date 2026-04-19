# HUD Pannels Design

## Purpose

This document captures the current HUD control-language direction and the first-pass production checklist for the clickable bridge-style panels.

## Control Language

- `Round red button`: immediate action or trigger.
- `Toggle switch`: binary state change.
- `3-position selector`: exclusive mode choice.
- `Lever`: timed, high-commitment system.
- `Guarded switch`: dangerous or high-consequence action.
- `Rectangular terminal key`: utility command or screen transition.
- `Amber`: selected, armed, or primed.
- `Gray`: inactive, offline, or unavailable.
- `Red`: critical or dangerous active state.
- `Blue/Cyan`: navigation, sensors, and information systems.
- `Green`: stable or healthy state.

## HUD Production Checklist

| System | HUD Element | Control Type | Core States | Priority | Notes |
|---|---|---|---|---|---|
| Beam Weapons | Beam mode selector | 2-position toggle | `Rapid Fire`, `Concentrated`, `Disabled` | High | Current art supports this immediately. |
| Missiles | Missile mode selector | 3-position selector | `Heavy`, `Fast`, `AAA`, `Disabled` | High | One active lane should read faster than separate action buttons. |
| ECM | ECM control | Lever | `Primed`, `Active`, `Recharging`, `Jammed` | High | Strong candidate for dramatic tactile feedback. |
| Targeting | Target lock panel | Button + readout | `No Target`, `Locking`, `Locked`, `Lost` | High | Pair with current tactical lock flow. |
| Countermeasures | Flare/decoy deploy | Round action button | `Ready`, `Firing`, `Cooldown`, `Empty` | High | Red trigger language fits this well. |
| Point Defense | PD mode selector | Toggle or selector | `Off`, `Auto`, `Missiles Only`, `All Threats` | High | Useful for missile-heavy fights. |
| Engines | Engine mode panel | Toggle or selector | `Cruise`, `Combat`, `Boost`, `Damaged` | High | Important once larger maps and sectors matter more. |
| Power | Power allocation panel | Slider / dial / mini terminal | `Weapons`, `Engines`, `Sensors`, `Defense` | High | Existing power overlay can inform this panel. |
| Damage Control | Repair / subsystem panel | Terminal keys | `Healthy`, `Damaged`, `Repairing`, `Offline` | High | Strong campaign and x-ray synergy. |
| Navigation | Sector map / transfer panel | Terminal button | `Available`, `Locked`, `Charging`, `In Transit` | High | Needed for zone-based flow. |
| Sensors | Radar mode control | Toggle | `Active`, `Passive`, `Hidden`, `Jammed` | Medium | Works well with ECM and stealth. |
| Sensors | Sensor gain dial | Rotary dial | `Low`, `Medium`, `High`, `Overload` | Medium | Good if detection becomes more tactical. |
| Weapons | Fire group selector | Selector | `Group A`, `Group B`, `Linked`, `Safe` | Medium | Good for larger hulls with more mixed hardpoints. |
| Weapons | Overcharge control | Guarded switch | `Safe`, `Armed`, `Active`, `Overheat` | Medium | High drama, add once overload flow is stable. |
| Survival | Emergency vent/purge | Guarded switch | `Closed`, `Armed`, `Purging`, `Cooldown` | Medium | Fits reactor or heat systems. |
| Defense | Shield mode / bias | Dial / selector | `Front`, `Rear`, `Balanced`, `Offline` | Medium | Useful if shield play becomes more manual. |
| Ship Handling | Vector assist | Toggle | `On`, `Off`, `Damaged` | Medium | Supports a more technical flight model. |
| Ship Handling | Autopilot | Terminal key | `Off`, `Set`, `Engaged`, `Interrupted` | Medium | Better for campaign and long-range navigation. |
| Targeting | Target cycle controls | Buttons | `Prev`, `Next`, `Nearest`, `Highest Threat` | Medium | Can be a compact cluster. |
| Warnings | Missile warning panel | Readout | `Clear`, `Inbound`, `Multiple`, `Critical` | Medium | High value even as a simple display-only panel. |
| Strategy | Squadron orders panel | Terminal keys | `Attack`, `Defend`, `Hold`, `Regroup` | Medium | Valuable once multi-ship command is deeper. |
| Strategy | Build/deploy terminal | Terminal screen | `Available`, `Insufficient Resources`, `Queued` | Medium | More relevant around bases and starbases. |
| Campaign | Sector status panel | Readout | `Clear`, `Hostile`, `Contested`, `Friendly` | Medium | Helps glue the sector-map structure together. |
| Campaign | Logistics panel | Terminal screen | `Supplied`, `Low`, `Critical`, `Cut Off` | Low | Important later, not first-wave. |
| Campaign | Intel/faction terminal | Terminal screen | `New Report`, `Viewed`, `Urgent` | Low | Good worldbuilding utility panel. |
| Utility | Alert state indicator | Large state panel | `Green`, `Amber`, `Red`, `Critical` | High | Strong atmosphere and combat readability. |
| Utility | Heat gauge | Meter / bar | `Cool`, `Warm`, `Hot`, `Overheat` | High | Needed if overload and reactor stress stay central. |
| Utility | Reactor status | Readout | `Stable`, `Strained`, `Critical`, `Offline` | High | Good companion to ECM and overload systems. |
| Utility | Ship integrity silhouette | Damage readout | `Healthy`, `Section Damage`, `Critical` | High | One of the highest-value displays. |
| Utility | Objective panel | Terminal strip | `Primary`, `Secondary`, `Updated` | Medium | Existing objective HUD can evolve into this. |
| Utility | Comms panel | Terminal button/readout | `Idle`, `Incoming`, `Open`, `Encrypted` | Low | Strong flavor and campaign utility. |

## First Production Wave

1. Beam mode selector
2. Missile mode selector
3. ECM lever
4. Target lock panel
5. Point defense mode
6. Engine mode panel
7. Alert state indicator
8. Heat gauge
9. Reactor status panel
10. Ship integrity display
11. Countermeasure deploy button
12. Sector map / transfer terminal

## Asset File Targets

Current renderer integration looks for these exports in `assets/hud_panels/`:

- `beam_mode_rapid.png`
- `beam_mode_concentrated.png`
- `missile_mode_heavy.png`
- `missile_mode_fast.png`
- `missile_mode_aaa.png`
- `ecm_mode_primed.png`
- `ecm_mode_active.png`

If the files are not present yet, the game falls back to a simple drawn version of the same control panels.
