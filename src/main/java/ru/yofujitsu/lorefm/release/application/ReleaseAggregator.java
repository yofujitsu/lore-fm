package ru.yofujitsu.lorefm.release.application;

import ru.yofujitsu.lorefm.release.domain.*;
import java.net.URI;
import java.text.Normalizer;
import java.util.*;

public final class ReleaseAggregator {
    private record Candidate(ProviderRelease item, int order) {}

    public List<Release> aggregate(List<ProviderRelease> found, ReleaseSearchQuery query) {
        var groups = new LinkedHashMap<String, List<Candidate>>();
        for (int index = 0; index < found.size(); index++) {
            var incoming = found.get(index);
            var release = incoming.release();
            String title = normalize(release.title());
            String artist = primary(release);
            String key = title.isBlank() || artist.isBlank() ? release.id().value() : title + "\u0000" + artist;
            var variants = groups.computeIfAbsent(key, ignored -> new ArrayList<>());
            int match = -1;
            for (int i = 0; i < variants.size(); i++) {
                if (compatible(variants.get(i).item().release(), release)) { match = i; break; }
            }
            if (match < 0) variants.add(new Candidate(incoming, index));
            else {
                var old = variants.get(match);
                variants.set(match, new Candidate(merge(old.item(), incoming), old.order()));
            }
        }
        return groups.values().stream().flatMap(List::stream)
                .sorted(Comparator.<Candidate>comparingInt(c -> relevance(c.item().release(), query.query())).reversed()
                        .thenComparing(Comparator.comparingInt((Candidate c) -> c.item().confidence()).reversed())
                        .thenComparing(Comparator.comparingInt((Candidate c) -> typeRelevance(c.item().release().type())).reversed())
                        .thenComparingInt(Candidate::order))
                .limit(query.limit()).map(c -> c.item().release()).toList();
    }

    public static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\p{M}]+", " ").strip();
    }
    private static String primary(Release release) {
        return release.artists().isEmpty() ? "" : normalize(release.artists().getFirst().name());
    }
    private static boolean compatible(Release a, Release b) {
        return (a.releaseYear() == null || b.releaseYear() == null || a.releaseYear().equals(b.releaseYear()))
                && (a.type() == b.type() || a.type() == ReleaseType.UNKNOWN || b.type() == ReleaseType.UNKNOWN);
    }
    private static int precedence(Release release) {
        return release.sources().stream().mapToInt(s -> s.provider().ordinal()).min().orElse(100);
    }
    private static ProviderRelease merge(ProviderRelease left, ProviderRelease right) {
        var a = left.release(); var b = right.release();
        if (precedence(b) < precedence(a)) { var swap = a; a = b; b = swap; }
        var sources = new LinkedHashMap<String, ReleaseSource>();
        for (var release : List.of(a, b)) for (var source : release.sources()) {
            sources.putIfAbsent(source.provider() + ":" + source.externalId(), source);
        }
        var artwork = validArtwork(a.artwork()) ? a.artwork() : validArtwork(b.artwork()) ? b.artwork() : null;
        var merged = new Release(a.id(), a.title(), a.artists(), a.releaseYear() == null ? b.releaseYear() : a.releaseYear(),
                a.type() == ReleaseType.UNKNOWN ? b.type() : a.type(), artwork,
                sources.values().stream().sorted(Comparator.comparingInt(s -> s.provider().ordinal())).toList());
        return new ProviderRelease(merged, Math.max(left.confidence(), right.confidence()));
    }
    private static boolean validArtwork(Artwork artwork) {
        if (artwork == null || artwork.url() == null) return false;
        try {
            var uri = URI.create(artwork.url());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException ex) { return false; }
    }
    private static int relevance(Release release, String text) {
        String query = normalize(text);
        if (query.isBlank()) return 0;
        String title = normalize(release.title()); String artist = primary(release);
        if (query.equals(title + " " + artist) || query.equals(artist + " " + title)) return 400;
        if (query.equals(title)) return 300;
        if (query.equals(artist)) return 200;
        var tokens = query.split(" ");
        String combined = title + " " + artist;
        return (int) Arrays.stream(tokens).filter(combined::contains).count() * 100 / tokens.length;
    }
    private static int typeRelevance(ReleaseType type) {
        return switch (type) {
            case ALBUM -> 6;
            case EP -> 5;
            case MIXTAPE -> 4;
            case COMPILATION -> 3;
            case SINGLE -> 2;
            default -> 0;
        };
    }
}
