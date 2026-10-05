package ru.yofujitsu.lorefm.release.domain;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ReleaseTest {
    @Test
    void snapshotsListsAndAcceptsUnknownArtworkAndYear() {
        var artists = new ArrayList<>(List.of(new Artist("Bladee")));
        var sources = new ArrayList<>(List.of(new ReleaseSource(Provider.SPOTIFY, "123", "https://open.spotify.com/album/123")));
        var release = new Release(new ReleaseId("spotify:123"), "Cold Visions", artists, null,
                ReleaseType.ALBUM, null, sources);
        artists.clear();
        sources.clear();
        assertThat(release.artists()).extracting(Artist::name).containsExactly("Bladee");
        assertThat(release.sources()).hasSize(1);
        assertThatThrownBy(() -> release.artists().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(release.artwork()).isNull();
        assertThat(release.releaseYear()).isNull();
    }

    @Test
    void rejectsBlankIdentityAndTitle() {
        assertThatThrownBy(() -> new ReleaseId(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Release(new ReleaseId("spotify:123"), " ", List.of(new Artist("Bladee")),
                null, ReleaseType.UNKNOWN, null, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void serializesQualifiedIdAsScalar() {
        var json = new JsonMapper().writeValueAsString(new ReleaseId("spotify:123"));
        assertThat(json).isEqualTo("\"spotify:123\"");
    }
}
