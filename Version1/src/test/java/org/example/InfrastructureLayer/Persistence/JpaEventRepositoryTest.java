package org.example.InfrastructureLayer.Persistence;

import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaEventRepositoryTest {

    @Mock
    private SpringDataEventRepository eventJpa;

    @Mock
    private SpringDataLayoutRepository layoutJpa;

    @Mock
    private SpringDataAreaRepository areaJpa;

    @Mock
    private SpringDataSeatRepository seatJpa;

    @Mock
    private SpringDataTicketRepository ticketJpa;

    private JpaEventRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JpaEventRepository(eventJpa, layoutJpa, areaJpa, seatJpa, ticketJpa);
    }

    @Test
    void savePersistsEventFields() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Event event = new Event(eventId, companyId, LocalDateTime.now(), "Tel Aviv", "Artist", "Concert", EventStatus.ACTIVE);
        event.setName("Test Event");
        event.setDescription("A description");
        event.setTags(List.of("music", "live"));
        event.setRating(4.5);

        repository.save(event);

        ArgumentCaptor<EventEntity> captor = ArgumentCaptor.forClass(EventEntity.class);
        verify(eventJpa).save(captor.capture());

        EventEntity stored = captor.getValue();
        assertEquals(eventId, stored.getId());
        assertEquals(companyId, stored.getCompanyId());
        assertEquals("Test Event", stored.getName());
        assertEquals("A description", stored.getDescription());
        assertEquals(List.of("music", "live"), stored.getTags());
        assertEquals(4.5, stored.getRating());
    }

    @Test
    void getByIdRestoresEvent() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        EventEntity entity = new EventEntity(
                eventId,
                "Test Event",
                companyId,
                "manager",
                "Tel Aviv",
                "A description",
                List.of("music", "live"),
                "Artist",
                "Concert",
                LocalDateTime.now(),
                4.5,
                EventStatus.ACTIVE,
                null,
                null,
                null,
                null
        );

        when(eventJpa.findById(eventId)).thenReturn(Optional.of(entity));

        Event loaded = repository.getById(eventId);

        assertNotNull(loaded);
        assertEquals(eventId, loaded.getEventId());
        assertEquals(companyId, loaded.getCompanyId());
        assertEquals("Test Event", loaded.getName());
        assertEquals("A description", loaded.getDescription());
        assertEquals(List.of("music", "live"), loaded.getTagsView());
        assertEquals(4.5, loaded.getRating());
    }
}
