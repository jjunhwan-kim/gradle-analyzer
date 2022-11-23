package com.example.analyzer.analyzer;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;

@Slf4j
@Component
public class GradleDependencyAnalyzer {

    private final String repoUrl = "https://repo1.maven.org/maven2";

    public void findFiles() {
        // Find .gradle files in project directory
    }

    public void analyze() {
        // Analyze .gradle file
    }

    public void parse() {
        // Extract group, artifact, version in .gradle file
    }

    private Document getDocument(String url) {
        try {
            log.info("Request, {}", url);
            return Jsoup.connect(url)
                    //.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("1.1.1.1", 8080)))
                    .get();
        } catch (Exception e) {
            log.info("Get failed, {}", url);
        }

        return null;
    }

    /**
     * Find license and related dependency
     *
     * @param dependency
     * @return
     */
    public ResolvedDependency resolve(Dependency dependency) {

        Map<String, ResolvedDependency> cache = new HashMap<>();
        return get(cache, dependency);
    }

    public ResolvedDependency get(Map<String, ResolvedDependency> cache, Dependency dependency) {

        Map<String, String> properties = new HashMap<>();

        if (!cache.containsKey(dependency.toString())) {
            String groupId = dependency.getGroupId();
            String artifactId = dependency.getArtifactId();
            String version = dependency.getVersion();
            String url = getUrl(groupId, artifactId, version);

            Document document = getDocument(url);

            if (document != null) {

                List<License> licenses = getLicenses(document);

                // Find project.version
                String projectVersion = getProjectVersion(document);
                properties.put("project.version", projectVersion);

                // Find properties include parent
                for (Map.Entry<String, String> entry : getAllProperties(document).entrySet()) {
                    if (properties.get(entry.getKey()) == null) {
                        properties.put(entry.getKey(), entry.getValue());
                    }
                }

                Map<String, Dependency> dependenciesOfDependencyManagement = getAllDependenciesOfDependencyManagement(document);

                // Find related dependencies
                Set<Dependency> relatedDependencies = new HashSet<>(getAllDependencies(document));
                List<ResolvedDependency> resolvedRelatedDependencies = new ArrayList<>();

                for (Dependency relatedDependency : relatedDependencies) {

                    String relatedDependencyVersion = getVersion(relatedDependency, dependenciesOfDependencyManagement, properties);

                    if (StringUtils.hasText(relatedDependencyVersion)) {
                        ResolvedDependency resolvedDependency = get(cache, new Dependency(relatedDependency.getGroupId(),
                                relatedDependency.getArtifactId(),
                                relatedDependencyVersion));
                        if (resolvedDependency != null) {
                            resolvedRelatedDependencies.add(resolvedDependency);
                        }
                    }
                }

                ResolvedDependency resolvedDependency = new ResolvedDependency(groupId, artifactId, version,
                        licenses,
                        resolvedRelatedDependencies);

                cache.put(dependency.toString(), resolvedDependency);
                return resolvedDependency;
            }
        } else {
            return cache.get(dependency.toString());
        }

        return null;
    }

    private String getVersion(Dependency dependency,
                              Map<String, Dependency> dependenciesOfDependencyManagement,
                              Map<String, String> properties) {
        String version = "";

        if (!StringUtils.hasText(dependency.getVersion())) {

            Dependency dependencyOfDependencyManagement = dependenciesOfDependencyManagement.get(dependency.getGroupId() + ":" + dependency.getArtifactId());

            if (dependencyOfDependencyManagement != null) {
                if (StringUtils.hasText(dependencyOfDependencyManagement.getVersion())) {
                    if (dependencyOfDependencyManagement.getVersion().startsWith("$")) {
                        String v = properties.get(dependencyOfDependencyManagement.getVersion().replace("$", "").replace("{", "").replace("}", ""));
                        if (v != null) {
                            version = v;
                        }
                    } else {
                        version = dependencyOfDependencyManagement.getVersion();
                    }
                }
            }
        } else if (dependency.getVersion().startsWith("$")) {
            String versionVariable = dependency.getVersion().replace("$", "").replace("{", "").replace("}", "");
            String v = properties.get(versionVariable);
            if (v != null) {
                version = v;
            }

        } else {
            version = dependency.getVersion();
        }

        return version;
    }

    private Map<String, String> getAllProperties(Document document) {

        String url;
        Map<String, String> properties = getProperties(document);

        Dependency parentDependency = getParentDependency(document);

        if (parentDependency != null) {
            url = getUrl(parentDependency.getGroupId(),
                    parentDependency.getArtifactId(),
                    parentDependency.getVersion());

            document = getDocument(url);

            if (document != null) {
                Map<String, String> parentProperties = getAllProperties(document);

                for (Map.Entry<String, String> entry : parentProperties.entrySet()) {
                    if (properties.get(entry.getKey()) == null) {
                        properties.put(entry.getKey(), entry.getValue());
                    }
                }
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

        List<Dependency> dependencies = new ArrayList<>();

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
                    //if (true) {
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
                            dependencies.add(new Dependency(
                                    groupId,
                                    artifactId,
                                    version));
                        }
                    }
                }
            }
        }

        return dependencies;
    }

    private List<Dependency> getAllDependencies(Document document) {

        String url;
        List<Dependency> dependencies = getDependencies(document);

        Dependency parentDependency = getParentDependency(document);

        if (parentDependency != null) {
            url = getUrl(parentDependency.getGroupId(),
                    parentDependency.getArtifactId(),
                    parentDependency.getVersion());

            document = getDocument(url);

            if (document != null) {
                dependencies.addAll(getAllDependencies(document));
            }
        }

        return dependencies;
    }

    private Map<String, Dependency> getAllDependenciesOfDependencyManagement(Document document) {

        String url;
        Map<String, Dependency> dependencies = getDependenciesOfDependencyManagement(document);

        Dependency parentDependency = getParentDependency(document);

        if (parentDependency != null) {
            url = getUrl(parentDependency.getGroupId(),
                    parentDependency.getArtifactId(),
                    parentDependency.getVersion());
            document = getDocument(url);

            if (document != null) {

                Map<String, Dependency> parentDependencies = getAllDependenciesOfDependencyManagement(document);

                for (Map.Entry<String, Dependency> entry : parentDependencies.entrySet()) {
                    if (dependencies.get(entry.getKey()) == null) {
                        dependencies.put(entry.getKey(), entry.getValue());
                    }
                }
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

        List<License> licenses = new ArrayList<>();

        Elements licensesElement = document.select("licenses");

        if (licensesElement.isEmpty()) {
            Dependency parentDependency = getParentDependency(document);

            if (parentDependency != null) {
                String groupId = parentDependency.getGroupId();
                String artifactId = parentDependency.getArtifactId();
                String version = parentDependency.getVersion();
                String url = getUrl(groupId, artifactId, version);

                Document parentDocument = getDocument(url);

                if (parentDocument != null) {
                    licenses.addAll(getLicenses(parentDocument));
                }
            }
        } else {
            Elements elements = licensesElement.select("license");

            for (Element e : elements) {
                License license = new License(
                        e.select("name").text(),
                        e.select("url").text(),
                        e.select("comment").text());

                licenses.add(license);
            }
        }

        return licenses;
    }

    private String getProjectVersion(Document document) {

        Elements projectVersionElement = document.select("project>version");

        if (!projectVersionElement.isEmpty()) {
            Element projectVersion = projectVersionElement.get(0);
            return projectVersion.text();
        } else {
            Elements projectParentVersionElement = document.select("project>parent>version");

            if (!projectParentVersionElement.isEmpty()) {
                Element projectParentVersion = projectParentVersionElement.get(0);
                return projectParentVersion.text();
            }
        }

        return null;
    }

    private String getUrl(String groupId, String artifactId, String version) {
        return this.repoUrl + "/"
                + groupId.replace(".", "/") + "/"
                + artifactId + "/"
                + version + "/"
                + artifactId + "-" + version + ".pom";
    }
}
