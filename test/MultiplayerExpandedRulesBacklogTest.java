import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiplayerExpandedRulesBacklogTest {

    @Test
    void everyExpandedRuleHasAConsideredDisabledDecision() {
        MultiplayerExpandedRulesBacklog backlog = new MultiplayerExpandedRulesBacklog();

        assertEquals(MultiplayerExpandedRulesBacklog.Expansion.values().length,
                backlog.decisions().size());
        for (MultiplayerExpandedRulesBacklog.Expansion expansion
                : MultiplayerExpandedRulesBacklog.Expansion.values()) {
            MultiplayerExpandedRulesBacklog.Decision decision = backlog.decision(expansion);
            assertNotNull(decision, expansion.name());
            assertTrue(decision.considered(), expansion.name());
            assertFalse(decision.enabledByDefault(), expansion.name());
            assertFalse(backlog.canEnableInV1(expansion), expansion.name());
            assertFalse(decision.requiredBeforeEnablement().isBlank(), expansion.name());
        }
    }

    @Test
    void reconnectAndInternetHostingRemainGatedBehindExplicitLaterWork() {
        MultiplayerExpandedRulesBacklog backlog = new MultiplayerExpandedRulesBacklog();

        assertTrue(backlog.decision(MultiplayerExpandedRulesBacklog.Expansion.RECONNECT)
                .requiredBeforeEnablement().contains("reconnect token"));
        assertTrue(backlog.decision(MultiplayerExpandedRulesBacklog.Expansion.INTERNET_HOSTING)
                .requiredBeforeEnablement().contains("relay"));
    }
}
