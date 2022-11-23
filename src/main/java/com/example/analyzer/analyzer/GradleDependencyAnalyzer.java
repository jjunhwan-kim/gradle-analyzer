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
import java.util.*;

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

    public List<Dependency> find(Dependency dependency) {

        Map<String, Dependency> cache = new HashMap<>();


        return get(cache, dependency);

    }

    private String getUrl(String groupId, String artifactId, String version) {
        return this.repoUrl + "/"
                + groupId.replace(".", "/") + "/"
                + artifactId + "/"
                + version + "/"
                + artifactId + "-" + version + ".pom";
    }

    public List<Dependency> get(Map<String, Dependency> cache, Dependency dependency) {

        List<Dependency> dependencies = new ArrayList<>();
        Map<String, String> properties = new HashMap<>();
        // Get information from maven repository

        if (!cache.containsKey(dependency.toString())) {
            cache.put(dependency.toString(), dependency);
            dependencies.add(dependency);


            String groupId = dependency.getGroupId();
            String artifactId = dependency.getArtifactId();
            String version = dependency.getVersion();
            String url = getUrl(groupId, artifactId, version);

            log.info("Get={}, URL={}", dependency, url);

            try {
                Document document = Jsoup.connect(url).get();

                List<License> licenses = getLicenses(document);
                dependency.setLicenses(licenses);

                // Find project.version
                Elements projectVersionElement = document.select("project>version");

                if (!projectVersionElement.isEmpty()) {
                    Element projectVersion = projectVersionElement.get(0);

                    properties.put("project.version", projectVersion.text());
                } else {
                    Elements projectParentVersionElement = document.select("project>parent>version");

                    if (!projectParentVersionElement.isEmpty()) {
                        Element projectParentVersion = projectParentVersionElement.get(0);
                        properties.put("project.version", projectParentVersion.text());
                    }
                }

                // Find properties include parent
                for (Map.Entry<String, String> entry : getAllProperties(document).entrySet()) {
                    if (properties.get(entry.getKey()) == null) {
                        properties.put(entry.getKey(), entry.getValue());
                    }
                }

                Map<String, Dependency> dependenciesOfDependencyManagement = getAllDependenciesOfDependencyManagement(document);

                // Find related dependencies
                List<Dependency> relatedDependencies = getDependencies(document);

                for (Dependency relatedDependency : relatedDependencies) {
                    String relatedDependencyVersion = "";

                    if (!StringUtils.hasText(relatedDependency.getVersion())) {

                        Dependency dependencyOfDependencyManagement = dependenciesOfDependencyManagement.get(relatedDependency.getGroupId() + ":" + relatedDependency.getArtifactId());

                        if (dependencyOfDependencyManagement != null) {
                            if (StringUtils.hasText(dependencyOfDependencyManagement.getVersion())) {
                                if (dependencyOfDependencyManagement.getVersion().startsWith("$")) {
                                    String v = properties.get(dependencyOfDependencyManagement.getVersion().replace("$", "").replace("{", "").replace("}", ""));
                                    if (v != null) {
                                        relatedDependencyVersion = v;
                                    }
                                } else {
                                    relatedDependencyVersion = dependencyOfDependencyManagement.getVersion();
                                }
                            }
                        }
                    } else if (relatedDependency.getVersion().startsWith("$")) {
                        String versionVariable = relatedDependency.getVersion().replace("$", "").replace("{", "").replace("}", "");
                        String v = properties.get(versionVariable);
                        if (v != null) {
                            relatedDependencyVersion = v;
                        }

                    } else {
                        relatedDependencyVersion = relatedDependency.getVersion();
                    }

                    if (StringUtils.hasText(relatedDependencyVersion)) {
                        List<Dependency> related = get(cache, new Dependency(relatedDependency.getGroupId(), relatedDependency.getArtifactId(), relatedDependencyVersion));

                        dependencies.addAll(related);
                    }
                }


            } catch (IOException e) {
                log.error("URL = {}", url);
            }
        }

        return dependencies;
    }

    private Map<String, String> getAllProperties(Document document) {

        String url;
        Map<String, String> properties = getProperties(document);

        Dependency parentDependency = getParentDependency(document);

        if (parentDependency != null) {
            url = getUrl(parentDependency.getGroupId(),
                    parentDependency.getArtifactId(),
                    parentDependency.getVersion());
            try {
                document = Jsoup.connect(url).get();

                Map<String, String> parentProperties = getAllProperties(document);

                for (Map.Entry<String, String> entry : parentProperties.entrySet()) {
                    if (properties.get(entry.getKey()) == null) {
                        properties.put(entry.getKey(), entry.getValue());
                    }
                }
            } catch (IOException e) {
                log.error("Get failed, {}", url);
            }
        }

        return properties;
    }

    private Map<String, String> getProperties(Document document) {

        Map<String, String> properties = new HashMap<>();

        Elements propertiesElement = document.select("project>properties");

        if (!propertiesElement.isEmpty()) {
            Element element = propertiesElement.get(0);

            for (Element e : element.children()) {
                if (properties.get(e.tagName()) == null) {
                    properties.put(e.tagName(), e.text());
                }
            }
        }

        return properties;
    }

    private List<Dependency> getDependencies(Document document) {

        List<Dependency> dependencyList = new ArrayList<>();

        Elements dependenciesElement = document.select("project>dependencies");

        if (!dependenciesElement.isEmpty()) {
            Elements elements = dependenciesElement.select("dependency");

            for (Element e : elements) {

                String groupId = "";
                String artifactId = "";
                String version = "";

                if (e.selectFirst("scope") != null) {
                    String scope = e.selectFirst("scope").text();

                    if ("compile".equals(scope) || "runtime".equals(scope)) {

                        if (e.selectFirst("groupId") != null) {
                            groupId = e.selectFirst("groupId").text();
                        }

                        if (e.selectFirst("artifactId") != null) {
                            artifactId = e.selectFirst("artifactId").text();
                        }

                        if (e.selectFirst("version") != null) {
                            version = e.selectFirst("version").text();
                        }

                        if (StringUtils.hasText(groupId) && StringUtils.hasText(artifactId)) {
                            dependencyList.add(new Dependency(
                                    groupId,
                                    artifactId,
                                    version));
                        }
                    }
                }
            }
        }

        return dependencyList;
    }

    private Map<String, Dependency> getAllDependenciesOfDependencyManagement(Document document) {

        String url;
        Map<String, Dependency> dependencies = getDependenciesOfDependencyManagement(document);

        Dependency parentDependency = getParentDependency(document);

        if (parentDependency != null) {
            url = getUrl(parentDependency.getGroupId(),
                    parentDependency.getArtifactId(),
                    parentDependency.getVersion());
            try {
                document = Jsoup.connect(url).get();

                Map<String, Dependency> parentDependencies = getAllDependenciesOfDependencyManagement(document);

                for (Map.Entry<String, Dependency> entry : parentDependencies.entrySet()) {
                    if (dependencies.get(entry.getKey()) == null) {
                        dependencies.put(entry.getKey(), entry.getValue());
                    }
                }
            } catch (IOException e) {
                log.error("Get failed, {}", url);
            }
        }

        return dependencies;
    }

    private Map<String, Dependency> getDependenciesOfDependencyManagement(Document document) {

        Map<String, Dependency> dependencies = new HashMap<>();

        Elements dependenciesElement = document.select("project>dependencyManagement>dependencies");

        if (!dependenciesElement.isEmpty()) {
            Elements elements = dependenciesElement.select("dependency");

            for (Element e : elements) {

                String groupId = "";
                String artifactId = "";
                String version = "";

                if (e.selectFirst("groupId") != null) {
                    groupId = e.selectFirst("groupId").text();
                }

                if (e.selectFirst("artifactId") != null) {
                    artifactId = e.selectFirst("artifactId").text();
                }

                if (e.selectFirst("version") != null) {
                    version = e.selectFirst("version").text();
                }

                if (StringUtils.hasText(groupId) && StringUtils.hasText(artifactId)) {
                    dependencies.put(
                            groupId + ":" + artifactId,
                            new Dependency(
                                    groupId,
                                    artifactId,
                                    version)
                    );
                }
            }
        }

        return dependencies;
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
                log.error("Get Parent License URL = {}", url);
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
