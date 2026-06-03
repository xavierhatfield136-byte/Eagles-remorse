# Economy, Logistics, And Markets

## Scope

Connect fuel, ammunition, supplies, ore, salvage, credits, repair pressure, contracts, markets, rationing, insurance, shortages, and AI reserves to live campaign decisions.

## Dependencies

- `EconomyLogisticsIndustrySystem`
- campaign travel attrition
- hub services
- strike and refit costs
- checkpoint persistence

## UI Flow

Resource board, market, contract, salvage, resupply, shipyard, and warning surfaces should show cost, shortage, recovery option, and later consequence.

## Data Ownership

The campaign resource ledger owns split stores and market pressure. Tactical systems consume projected readiness and cost outcomes.

## Save Impact

Persist split stores, market clocks, shortages, contracts, AI reserve depletion, rationing, maintenance debt, and travel attrition remainders.

## Asset Needs

No mandatory new assets. Market and cargo screens can use existing campaign UI language.

## Tests

Cover travel, mining, salvage, repair, refit, construction, strikes, market advancement, AI shortage response, and checkpoint restore.

## Non-Goals

This pack does not implement a full trading sim beyond alpha resource pressure.
