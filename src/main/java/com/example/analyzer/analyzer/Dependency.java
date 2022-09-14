package com.example.analyzer.analyzer;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class Dependency {

    private String groupId;
    private String artifactId;
    private String version;
    private List<License> licenses;
    private List<Dependency> children = new ArrayList<>();

    public Dependency(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public void setLicenses(List<License> licenses) {
        this.licenses = licenses;
    }

    public String getInformation() {
        return String.format("Group ID=%s, Artifact ID=%s, Version=%s, License=%s", groupId, artifactId, version, licenses);
    }

    public void addDependency(Dependency dependency) {
        this.children.add(dependency);
    }

    @Override
    public String toString() {
        return  groupId + ':' + artifactId + ':' + version;
    }

}
