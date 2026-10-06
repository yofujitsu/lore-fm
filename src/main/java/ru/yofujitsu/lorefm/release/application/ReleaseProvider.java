package ru.yofujitsu.lorefm.release.application;

import ru.yofujitsu.lorefm.release.domain.Provider;
import java.util.List;

public interface ReleaseProvider {
    Provider provider();
    default boolean enabled() { return true; }
    List<ProviderRelease> search(ReleaseSearchQuery query);
}
