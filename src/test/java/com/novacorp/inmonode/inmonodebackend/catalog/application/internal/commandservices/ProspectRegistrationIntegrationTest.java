package com.novacorp.inmonode.inmonodebackend.catalog.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.TestcontainersConfiguration;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.aggregates.Prospect;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.commands.RegisterProspectsCommand;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.model.commands.RegisterProspectsCommand.ProspectData;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.repositories.ProspectRepository;
import com.novacorp.inmonode.inmonodebackend.catalog.domain.services.ProspectCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Storing field prospects (US-04, US-11) against a real PostgreSQL: new ones are inserted and re-sent ones update
 * their contact info, so a re-sync never duplicates them.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ProspectRegistrationIntegrationTest {

    private static final long AGENT = 7L;
    private static final Instant REGISTERED_AT = Instant.parse("2026-10-08T09:00:00Z");

    @Autowired
    private ProspectCommandService prospectCommandService;

    @Autowired
    private ProspectRepository prospectRepository;

    @Test
    void newProspectsAreStoredWithTheDeviceIds() {
        var ana = UUID.randomUUID();
        var luis = UUID.randomUUID();

        var stored = register(AGENT, data(ana, "12345678", "Ana Quispe", "987654321"),
                data(luis, "87654321", "Luis Mamani", null));

        assertEquals(2, stored);
        var saved = find(ana);
        assertNotNull(saved.getId());
        assertEquals(AGENT, saved.getAgentId());
        assertEquals("Ana Quispe", saved.getFullName());
        assertEquals(REGISTERED_AT, saved.getRegisteredAt());
        assertNull(find(luis).getPhone());
    }

    @Test
    void resentProspectUpdatesItsContactInfoInsteadOfDuplicating() {
        var ana = UUID.randomUUID();
        register(AGENT, data(ana, "12345678", "Ana", "987654321"));

        var stored = register(AGENT, data(ana, "12345678", "Ana Quispe", "912345678"));

        assertEquals(1, stored);
        assertEquals(1, prospectRepository.findByProspectIds(List.of(ana)).size());
        assertEquals("Ana Quispe", find(ana).getFullName());
        assertEquals("912345678", find(ana).getPhone());
    }

    @Test
    void aProspectSentTwiceInTheSameBatchIsStoredOnceWithItsLastData() {
        var ana = UUID.randomUUID();

        var stored = register(AGENT, data(ana, "12345678", "Ana", null), data(ana, "12345678", "Ana Quispe", null));

        assertEquals(1, stored);
        assertEquals("Ana Quispe", find(ana).getFullName());
    }

    @Test
    void anotherAgentCannotOverwriteAProspect() {
        var ana = UUID.randomUUID();
        register(AGENT, data(ana, "12345678", "Ana", "987654321"));

        var stored = register(8L, data(ana, "12345678", "Someone else", null));

        assertEquals(0, stored);
        assertEquals("Ana", find(ana).getFullName());
        assertEquals(AGENT, find(ana).getAgentId());
    }

    @Test
    void anInvalidProspectRejectsTheWholeBatch() {
        var valid = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> register(AGENT, data(valid, "12345678", "Ana", null), data(UUID.randomUUID(), "", "Luis", null)));

        assertTrue(prospectRepository.findByProspectIds(List.of(valid)).isEmpty(), "nothing is stored");
    }

    @Test
    void registrationDateDefaultsToTheServerTime() {
        var ana = UUID.randomUUID();
        var before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        register(AGENT, new ProspectData(ana, "12345678", "Ana", null, null));

        assertFalse(find(ana).getRegisteredAt().isBefore(before));
    }

    private int register(long agentId, ProspectData... prospects) {
        return prospectCommandService.handle(new RegisterProspectsCommand(agentId, List.of(prospects)));
    }

    private static ProspectData data(UUID id, String document, String fullName, String phone) {
        return new ProspectData(id, document, fullName, phone, REGISTERED_AT);
    }

    private Prospect find(UUID prospectId) {
        return prospectRepository.findByProspectIds(List.of(prospectId)).getFirst();
    }
}
