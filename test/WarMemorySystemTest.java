import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WarMemorySystemTest {
    @Test void locationsShipsSurvivorsMemorialChronicleAndReportsPersistWithoutInventingFacts(){
        WarMemorySystem.State state=WarMemorySystem.bootstrap();
        WarMemorySystem.LocationRecord station=WarMemorySystem.recordLocation(state,"station-1","Lunar Relay","DARK_YELLOW",70,List.of("SHIPYARD"),true,false,"medical supplies",10);
        station=WarMemorySystem.recordLocation(state,"station-1","Lunar Relay","BRIGHT_YELLOW",70,List.of("SHIPYARD"),true,false,"reconstruction crew",12);
        assertFalse(station.ownershipHistory.isEmpty());
        WarMemorySystem.RepairResult repair=WarMemorySystem.repairLocation(state,"station-1",20,30); assertEquals(30,repair.resourcesSpent());
        assertTrue(WarMemorySystem.renameLocation(state,"station-1","Reconciliation Relay","liberation record",13));
        WarMemorySystem.ShipRecord ship=WarMemorySystem.recordShip(state,"ship-1","Resolute","FRIGATE","BRIGHT_YELLOW","Commodore Ilex","Held the relay"); ship.honors.add("Relay Star"); ship.victories=2; ship.scars.add("Port armor breach");
        WarMemorySystem.destroyShip(state,"ship-1",20,"reactor loss",false); assertTrue(WarMemorySystem.inheritHonors(state,"ship-1","ship-2","Resolute II")); assertEquals(1,WarMemorySystem.memorialSearch(state,"Resolute").size());
        WarMemorySystem.recordSurvivors(state,"survivors-1","ship-1","Engineering survivors",21,4,"crew recovery mission");
        WarMemorySystem.addChronicle(state,new WarMemorySystem.ChronicleEntry("battle-1",20,"battle","BRIGHT_YELLOW","station-1","ship-1","cmd-1","Bright held the relay","Resolute lost","battle-1",true));
        assertEquals(1,WarMemorySystem.filter(state,15,25,"BRIGHT","station-1","ship-1","cmd-1","battle").size());
        assertTrue(WarMemorySystem.exportAfterActionReport(state).contains("source=battle-1")); assertEquals(1,WarMemorySystem.historicalScenarioSeeds(state).size());
        WarMemorySystem.State restored=WarMemorySystem.restore(WarMemorySystem.serialize(state)); assertEquals("Reconciliation Relay",restored.locations.get("station-1").currentName); assertTrue(restored.ships.get("ship-1").destroyed); assertEquals(1,restored.survivors.size()); assertEquals(3,restored.chronicle.size());
    }

    @Test void strategicLedgerIngestionRetainsExactSourceProvenanceAndBoundsHistory(){
        StrategicCampaignExpansionSystem.State strategic=StrategicCampaignExpansionSystem.bootstrap(1L);
        WarMemorySystem.State memory=WarMemorySystem.bootstrap(); WarMemorySystem.ingestStrategicFacts(memory,strategic);
        for(int n=0;n<1200;n++)WarMemorySystem.addChronicle(memory,new WarMemorySystem.ChronicleEntry("e"+n,n,"territory","TEAM_C","t","","","fact "+n,"consequence","source-"+n,n%100==0));
        assertTrue(memory.chronicle.size()<=WarMemorySystem.MAX_CHRONICLE); assertTrue(WarMemorySystem.turningPointSummary(memory).stream().allMatch(line->line.contains("source")));
    }
}
