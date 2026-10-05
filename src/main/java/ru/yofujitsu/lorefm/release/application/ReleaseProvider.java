package ru.yofujitsu.lorefm.release.application;

import ru.yofujitsu.lorefm.release.domain.Provider;
import java.util.List;

public interface ReleaseProvider {
    Provider provider();
    List<ProviderRelease> search(ReleaseSearchQuery query);
}
