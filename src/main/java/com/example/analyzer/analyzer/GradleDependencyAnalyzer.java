package com.example.analyzer.analyzer;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GradleDependencyAnalyzer {

    private final String repoUrl = "https://repo1.maven.org/maven2";

    public void findFiles() {

        // Find .gradle files in project directory
    }

    public void analyze() {

        // analyze .gradle file


    }

    public void parse() {

        // find dependency line in .gradle file


    }

    private String getUrl(String groupId, String artifactId, String version) {
        return this.repoUrl + "/"
                + groupId.replace(".", "/") + "/"
                + artifactId + "/"
                + version + "/"
                + artifactId + "-" + version + ".pom";
    }

    public void get(Map<String, Dependency> dependencyMap, Dependency foundDependency) {

        // Get information from maven repository

        if (!dependencyMap.containsKey(foundDependency.toString())) {
            dependencyMap.put(foundDependency.toString(), foundDependency);

            String groupId = foundDependency.getGroupId();
            String artifactId = foundDependency.getArtifactId();
            String version = foundDependency.getVersion();
            String url = getUrl(groupId, artifactId, version);

            log.info("get={}, url={}", foundDependency, url);

            if (!(StringUtils.hasText(groupId) && StringUtils.hasText(artifactId) && StringUtils.hasText(version))) {
                return;
            }

            Connection connection = Jsoup.connect(url);

            try {
                Document document = connection.get();

                List<License> licenses = getLicenses(document);
                foundDependency.setLicenses(licenses);

                Map<String, String> propertiesMap = new HashMap<>();

                Elements propertiesElement = document.select("project>properties");

                if (!propertiesElement.isEmpty()) {
                    Element properties = propertiesElement.get(0);

                    for (Element e : properties.children()) {
                        propertiesMap.put(e.tagName(), e.text());
                    }
                }


                Elements dependenciesElement = document.select("project>dependencies");

                if (!dependenciesElement.isEmpty()) {
                    Elements dependencies = dependenciesElement.select("dependency");

                    for (Element dependency : dependencies) {

                        String childDependencyGroupId = dependency.select("groupId").text();
                        String childDependencyArtifactId = dependency.select("artifactId").text();

                        String childDependencyVersion = dependency.select("version").text();

                        if (childDependencyVersion.startsWith("${") && childDependencyVersion.endsWith("}") && childDependencyVersion.length() > 3) {
                            String substring = childDependencyVersion.substring(
                                    childDependencyVersion.indexOf("{") + 1,
                                    childDependencyVersion.length() - 1);

                            if (propertiesMap.containsKey(substring)) {
                                String v = propertiesMap.get(substring);
                                if (v != null) {
                                    childDependencyVersion = v;
                                }
                            }
                        }

                        Dependency childDependency = new Dependency(
                                childDependencyGroupId,
                                childDependencyArtifactId,
                                childDependencyVersion);

                        foundDependency.addDependency(childDependency);

                        get(dependencyMap, childDependency);
                    }
                }
            } catch (IOException e) {
                log.error("Connection get error={}", url);
//                throw new RuntimeException(e);
            }
        }
    }

    private Dependency getParentDependency(Document document) {
        Elements parentElement = document.select("parent");

        if (parentElement.isEmpty()) {
            return null;
        }

        String parentGroupId = parentElement.select("groupId").text();
        String parentArtifactId = parentElement.select("artifactId").text();
        String parentVersion = parentElement.select("version").text();

        return new Dependency(parentGroupId, parentArtifactId, parentVersion);
    }

    private List<License> getLicenses(Document document) {

        List<License> foundLicenses = new ArrayList<>();

        Elements licensesElement = document.select("licenses");

        if (licensesElement.isEmpty()) {
            Dependency parentDependency = getParentDependency(document);

            if (parentDependency == null) {
                return foundLicenses;
            }

            String groupId = parentDependency.getGroupId();
            String artifactId = parentDependency.getArtifactId();
            String version = parentDependency.getVersion();
            String url = getUrl(groupId, artifactId, version);

            Connection connection = Jsoup.connect(url);

            try {
                Document parentDocument = connection.get();
                foundLicenses = getLicenses(parentDocument);

            } catch (IOException e) {
                log.error("Connection get error={}", url, e);
            }

        } else {
            Elements licenses = licensesElement.select("license");

            for (Element license : licenses) {
                License foundLicense = new License(
                        license.select("name").text(),
                        license.select("url").text(),
                        license.select("comment").text());

                foundLicenses.add(foundLicense);
            }
        }

        return foundLicenses;
    }
}
