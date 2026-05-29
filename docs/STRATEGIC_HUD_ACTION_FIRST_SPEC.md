# Strategic HUD Action-First Spec

Date: 2026-05-14  
Status: Active implementation guide

## Purpose

This document is the active specification for strategic HUD usability.

Its purpose is not to define the campaign's world systems.

Its purpose is to define how the player sees, understands, and executes strategic actions.

## Core Rule

Every available player action must be represented as a visible button, selector, toggle, or command plate.

Keyboard shortcuts may remain, but they are optional accelerators, not required controls.

## Why This Matters Now

The campaign now has enough systemic depth that usability is the main bottleneck.

The next major quality gain will come from:

- clearer action visibility
- clearer action grouping
- clearer disabled-state explanations
- less text used as a substitute for control affordance

## Required Principles

1. `No hidden mandatory commands.`
   Do not require Ctrl, Shift, middle-click, or obscure key paths for normal strategic play.

2. `Selection must always imply visible affordances.`
   If the player selects a hub, contact, target, route point, or site, the game must clearly show what can be done next.

3. `Disabled actions must explain why.`
   If an action is unavailable, the UI should say why in direct operational language.

4. `Recommended actions should stand out.`
   Each context should make the most likely or safest next step visually obvious.

5. `Preview before commitment.`
   Risk, cost, range, retaliation, and route implications should be previewed before the player commits where practical.

6. `Less text-as-control.`
   Explanatory text supports actions, but should not be the only way the player learns what is possible.

## Strategic HUD Goals

- visible action buttons for every important command
- consistent selected-object panel structure
- simpler tab behavior with clearer information hierarchy
- primary and secondary action grouping that stays stable across contexts
- stronger route, strike, docking, and site-resolution previews
- faster scanning for "what is selected", "what can I do", and "why is this disabled"

## Required Action Coverage

The visible HUD path must cover:

- plotting, engaging, and canceling travel
- waypoint setting
- approaching and docking
- site entry
- site-resolution choice
- scans, sweeps, and intel actions
- posture selection
- support calls
- hostile target tracking
- torpedo, sortie, and atomic strike actions
- confirmation flows for high-commitment actions

## Required Readout Coverage

The strategic HUD must clearly surface:

- current selection
- recommended next action
- action availability and blocked reasons
- distance and ETA where relevant
- route cost / fuel / exposure preview where relevant
- strike feasibility and retaliation risk where relevant
- local site cost / risk / reward summary where relevant

## Current Priorities

1. standardize selected-object presentation
2. improve preview quality before commitment
3. reduce shortcut dependency further
4. make strike and logistics constraints easier to read at a glance
5. keep adding visible action paths wherever a shortcut still feels easier than the HUD

## Relationship To Other Docs

- `STRATEGIC_CAMPAIGN_MAP_SPEC.md` defines what the campaign is
- `STRATEGIC_CAMPAIGN_REACTIVE_THEATER_CHECKLIST.md` defines current reactive systems work
- `STRATEGIC_HUD_ACTION_FIRST_SPEC.md` defines how the player should command those systems
- superseded redesign notes were removed to avoid conflicting guidance
