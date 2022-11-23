package com.example.analyzer.analyzer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class ResolvedDependency {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final List<License> licenses;
    private final List<ResolvedDependency> relatedDependencies;

    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + version + " " + licenses;
    }
}
