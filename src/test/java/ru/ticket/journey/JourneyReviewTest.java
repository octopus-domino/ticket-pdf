package ru.ticket.journey;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class JourneyReviewTest {
  @Test void certificateCollectsCourseFeedbackAndRevocationClosesIt() {
    var document = new JourneyEntity(); document.id = "certificate"; document.kind = "CERTIFICATE";
    when(journeys.lockById(document.id)).thenReturn(java.util.Optional.of(document));
    when(documents.status(document.id)).thenReturn(new JourneyContentPolicy.Status("ACTIVE", null, "", false));
    var service = new JourneyReviewService(reviews, journeys, documents, access);
    service.submit(document.id, new JourneyReviewService.Input(5, "Полезный курс", "b".repeat(64)));
    assertThat(service.list(document.id, null, "owner", 0).items().get(0).message()).isEqualTo("Полезный курс");
    document.revoked = true;
    assertThatThrownBy(() -> service.submit(document.id, new JourneyReviewService.Input(4, "Again", "b".repeat(64))))
        .hasMessage("journey.review.closed");
  }
  @Autowired JourneyReviewRepository reviews;
  private final JourneyRepository journeys = mock(JourneyRepository.class);
  private final JourneyService documents = mock(JourneyService.class);
  private final PublicationAccess access = mock(PublicationAccess.class);

  @Test
  void publicSubmissionUpdatesOneResponseButOnlyTheOwnerCanReadReviews() {
    var document = new JourneyEntity();
    document.id = "price-tag";
    document.kind = "PRICETAG";
    when(journeys.lockById(document.id)).thenReturn(java.util.Optional.of(document));
    when(documents.status(document.id))
        .thenReturn(new JourneyContentPolicy.Status("ACTIVE", null, "", false));
    var service = new JourneyReviewService(reviews, journeys, documents, access);
    String key = "a".repeat(64);
    service.submit(document.id, new JourneyReviewService.Input(5, "Great", key));
    service.submit(document.id, new JourneyReviewService.Input(3, "Changed my rating", key));
    assertThat(reviews.countByJourneyId(document.id)).isEqualTo(1);
    assertThatThrownBy(() -> service.list(document.id, null, null, 0))
        .hasMessage("Invalid management key");
    var result = service.list(document.id, null, "owner-id", 0);
    verify(access).document(document.id, "owner-id");
    assertThat(result.total()).isEqualTo(1);
    assertThat(result.average()).isEqualTo(3);
    assertThat(result.items().get(0).message()).isEqualTo("Changed my rating");
    assertThat(reviews.findAll().get(0).visitorHash).isNotEqualTo(key);
    document.revoked = true;
    assertThatThrownBy(
            () -> service.submit(document.id, new JourneyReviewService.Input(4, "Again", key)))
        .hasMessage("journey.review.closed");
  }
}
