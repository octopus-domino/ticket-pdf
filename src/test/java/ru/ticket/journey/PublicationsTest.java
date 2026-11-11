package ru.ticket.journey;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import ru.ticket.common.ApiException;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({
  JourneyService.class,
  JourneySiteService.class,
  JourneyContentPolicy.class,
  PublicationsService.class,
  PublicationAccess.class,
  ShortCodeGenerator.class,
  ObjectMapper.class,
  ru.ticket.workflow.SiteAddressService.class
})
class PublicationsTest {
  @Autowired JourneyService documents;
  @Autowired JourneySiteService sites;
  @Autowired PublicationsService registry;

  private JourneyService.Input menu(String title, String site) {
    var data = new HashMap<>(Map.of("items", "Tea 100"));
    if (site != null) data.put("site", site);
    return new JourneyService.Input(JourneyService.Kind.MENU, title, data, List.of());
  }

  private JourneySiteService.Definition site(String slug) {
    return new JourneySiteService.Definition(
        slug,
        JourneyService.Kind.MENU,
        "Menu site",
        "Menu",
        "sage",
        List.of(new JourneySiteService.Field("items", "Items", "textarea", true)));
  }

  @Test
  void registryIsPrivateAndPaginatedWithStableBoundaries() {
    for (int i = 0; i < 23; i++) documents.create(menu("Own menu " + i, null), "owner");
    documents.create(menu("Foreign menu", null), "other");
    var first = registry.documents("owner", 0, "", "");
    var second = registry.documents("owner", 1, "", "");
    assertThat(first.total()).isEqualTo(23);
    assertThat(first.items()).hasSize(20);
    assertThat(second.items()).hasSize(3);
    var ids = new HashSet<String>();
    first.items().forEach(item -> ids.add(item.document().id()));
    second.items().forEach(item -> ids.add(item.document().id()));
    assertThat(ids).hasSize(23);
    assertThat(registry.documents("owner", 0, "", "FOREIGN").total()).isZero();
  }

  @Test
  void siteOwnerCanManageVisitorDocumentsButAnotherAccountCannot() {
    sites.create(site("owned-menu-site"), "owner");
    var issued = documents.create(menu("Visitor menu", "owned-menu-site")).get(0);
    var id = issued.document().id();
    assertThat(registry.documents("owner", 0, "owned-menu-site", "").total()).isEqualTo(1);
    assertThat(registry.documents("other", 0, "", "").total()).isZero();
    assertThatThrownBy(() -> documents.manageOwned(id, "other")).isInstanceOf(ApiException.class);
    assertThat(documents.updateOwned(id, "owner", menu("Updated", "owned-menu-site")).title())
        .isEqualTo("Updated");
    documents.revokeOwned(id, "owner");
    assertThat(registry.documents("owner", 0, "", "").items().get(0).status().state())
        .isEqualTo("REVOKED");
  }

  @Test
  void legacyClaimRequiresTheOriginalSecretAndNeverTransfersAnExistingOwner() {
    var legacy = documents.create(menu("Legacy", null)).get(0);
    var claim =
        new PublicationsService.Claim("document", legacy.document().id(), legacy.managementKey());
    assertThat(
            registry
                .claim(
                    "owner",
                    List.of(new PublicationsService.Claim("document", claim.id(), "wrong")))
                .get("accepted"))
        .isZero();
    assertThat(registry.claim("owner", List.of(claim)).get("accepted")).isEqualTo(1);
    assertThat(registry.claim("other", List.of(claim)).get("accepted")).isZero();
    var legacySite = sites.create(site("legacy-menu-site"));
    assertThat(
            registry
                .claim(
                    "owner",
                    List.of(
                        new PublicationsService.Claim(
                            "site", "legacy-menu-site", legacySite.managementKey())))
                .get("accepted"))
        .isEqualTo(1);
    assertThat(registry.sites("owner", 0).total()).isEqualTo(1);
  }
}
