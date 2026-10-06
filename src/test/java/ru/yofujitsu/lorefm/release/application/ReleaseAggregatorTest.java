package ru.yofujitsu.lorefm.release.application;

import org.junit.jupiter.api.Test;
import ru.yofujitsu.lorefm.release.domain.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ReleaseAggregatorTest {
    static ProviderRelease item(Provider provider, String id, String title, String artist, Integer year, ReleaseType type, Artwork artwork, int confidence) {
        return new ProviderRelease(new Release(new ReleaseId(provider.name().toLowerCase(Locale.ROOT) + ":" + id), title,
                List.of(new Artist(artist)), year, type, artwork,
                List.of(new ReleaseSource(provider, id, "https://example.com/" + id))), confidence);
    }
    static ReleaseSearchQuery query(String text, int limit) { return new ReleaseSearchQuery(text, limit, Set.of()); }

    @Test void mergesComparisonEquivalentMetadataWithoutChangingDisplayOrCanonicalId() {
        var art = new Artwork("https://example.com/cover.jpg", 500, 500, Provider.MUSICBRAINZ);
        var spotify = item(Provider.SPOTIFY, "sp", " Cold—Visions ", "Björk", 2024, ReleaseType.ALBUM, null, 100);
        var mb = item(Provider.MUSICBRAINZ, "mb", "cold   visions", "Björk", 2024, ReleaseType.ALBUM, art, 99);
        var aggregator = new ReleaseAggregator();
        var forward = aggregator.aggregate(List.of(spotify, mb), query("Cold Visions", 10));
        var reversed = aggregator.aggregate(List.of(mb, spotify), query("Cold Visions", 10));
        assertThat(forward).containsExactlyElementsOf(reversed).hasSize(1);
        assertThat(forward.getFirst().id().value()).isEqualTo("spotify:sp");
        assertThat(forward.getFirst().title()).isEqualTo(" Cold—Visions ");
        assertThat(forward.getFirst().artists()).containsExactly(new Artist("Björk"));
        assertThat(forward.getFirst().sources()).extracting(ReleaseSource::provider).containsExactly(Provider.SPOTIFY, Provider.MUSICBRAINZ);
        assertThat(forward.getFirst().artwork()).isEqualTo(art);
    }

    @Test void keepsEditionsDifferentKnownYearsAndDifferentReleaseTypesSeparate() {
        var items = List.of(
                item(Provider.SPOTIFY, "base", "Discovery", "Daft Punk", 2001, ReleaseType.ALBUM, null, 100),
                item(Provider.MUSICBRAINZ, "deluxe", "Discovery (Deluxe)", "Daft Punk", 2001, ReleaseType.ALBUM, null, 100),
                item(Provider.MUSICBRAINZ, "remaster", "Discovery", "Daft Punk", 2021, ReleaseType.ALBUM, null, 100),
                item(Provider.MUSICBRAINZ, "single", "Discovery", "Daft Punk", 2001, ReleaseType.SINGLE, null, 100),
                item(Provider.MUSICBRAINZ, "same", "Discovery", "Daft Punk", null, ReleaseType.UNKNOWN, null, 100));
        var found = new ReleaseAggregator().aggregate(items, query("Discovery", 20));
        assertThat(found).hasSize(4);
        assertThat(found.getFirst().sources()).hasSize(2);
    }

    @Test void ranksExactAndCombinedMatchesBeforeConfidenceAndAppliesLimitAfterMerging() {
        var vague = item(Provider.SPOTIFY, "vague", "Cold weather", "Someone", 2000, ReleaseType.ALBUM, null, 100);
        var title = item(Provider.MUSICBRAINZ, "title", "Cold Visions", "Bladee", 2024, ReleaseType.ALBUM, null, 50);
        var duplicate = item(Provider.SPOTIFY, "duplicate", "Cold Visions", "Bladee", 2024, ReleaseType.ALBUM, null, 100);
        var artist = item(Provider.MUSICBRAINZ, "artist", "Different", "Cold Visions", 2000, ReleaseType.EP, null, 99);
        var aggregator = new ReleaseAggregator();
        assertThat(aggregator.aggregate(List.of(vague, title, duplicate, artist), query("Cold Visions", 2)))
                .extracting(Release::id).containsExactly(new ReleaseId("spotify:duplicate"), new ReleaseId("musicbrainz:artist"));
        assertThat(aggregator.aggregate(List.of(vague, title), query("Bladee Cold Visions", 10)).getFirst().title()).isEqualTo("Cold Visions");
        assertThat(aggregator.aggregate(List.of(vague, title), query("Bladee", 10)).getFirst().title()).isEqualTo("Cold Visions");
    }

    @Test void preservesProviderOrderForTiesAndPrefersRelevantTypesAndConfidence() {
        var first = item(Provider.MUSICBRAINZ, "first", "First", "Artist", 2000, ReleaseType.ALBUM, null, 80);
        var second = item(Provider.MUSICBRAINZ, "second", "Second", "Artist", 2000, ReleaseType.ALBUM, null, 80);
        var single = item(Provider.MUSICBRAINZ, "single", "Single", "Artist", 2000, ReleaseType.SINGLE, null, 80);
        var confident = item(Provider.MUSICBRAINZ, "confident", "Confident", "Artist", 2000, ReleaseType.EP, null, 90);
        assertThat(new ReleaseAggregator().aggregate(List.of(single, first, second, confident), query("Artist", 10)))
                .extracting(Release::title).containsExactly("Confident", "First", "Second", "Single");
    }
}
