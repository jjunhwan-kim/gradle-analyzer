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
    private Map<String, Document> documentCache = new HashMap<>();

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

        if (documentCache.containsKey(url)) {
            return documentCache.get(url);
        }

        try {
            log.info("Request, {}", url);
            Document document = Jsoup.connect(url)
                    //.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("1.1.1.1", 8080)))
                    .get();
            documentCache.put(url, document);
            return document;
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

        Map<Dependency, ResolvedDependency> dependencies = new HashMap<>();
        Set<Dependency> visit = new HashSet<>();

        return get(dependency, dependencies, visit);
    }

    public ResolvedDependency get(Dependency dependency, Map<Dependency, ResolvedDependency> dependencyCache, Set<Dependency> visit) {

        Map<String, String> properties = new HashMap<>();

        if (dependencyCache.containsKey(dependency)) {
            return dependencyCache.get(dependency);
        }

        if (visit.contains(dependency)) {
            return null;
        }

        visit.add(dependency);

        String groupId = dependency.getGroupId();
        String artifactId = dependency.getArtifactId();
        String version = dependency.getVersion();
        String url = getUrl(groupId, artifactId, version);

        properties.put("pom.groupId", groupId);
        properties.put("project.groupId", groupId);

        Document document = getDocument(url);

        if (document == null) {
            return null;
        }

        // FInd licenses
        List<License> licenses = getLicenses(document);

        // Find project.version
        String projectVersion = getProjectVersion(document);
        properties.put("pom.version", projectVersion);
        properties.put("project.version", projectVersion);
        properties.put("version", projectVersion);

        // Find properties include parent
        for (Map.Entry<String, String> entry : getAllProperties(document).entrySet()) {
            if (properties.get(entry.getKey()) == null) {
                properties.put(entry.getKey(), entry.getValue());
            }
        }

        // Find dependencyManagement
        Map<String, Dependency> dependenciesOfDependencyManagement = getAllDependenciesOfDependencyManagement(document);

        // Find related dependencies
        Set<Dependency> relatedDependencies = new HashSet<>(getAllDependencies(document));
        List<ResolvedDependency> resolvedRelatedDependencies = new ArrayList<>();

        for (Dependency relatedDependency : relatedDependencies) {

            String relatedDependencyVersion = getActualVersion(relatedDependency, dependenciesOfDependencyManagement, properties);

            String relatedDependencyGroupId = relatedDependency.getGroupId();

            if (relatedDependencyGroupId.startsWith("${") && version.endsWith("}")) {
                String groupIdVariable = relatedDependencyGroupId.replace("$", "").replace("{", "").replace("}", "");
                if (properties.containsKey(groupIdVariable)) {
                    relatedDependencyGroupId = properties.get(groupIdVariable);
                }
            }

            if (relatedDependency.equals(dependency)) {
                continue;
            }

            if (StringUtils.hasText(relatedDependencyVersion)) {
                ResolvedDependency resolvedDependency = get(new Dependency(relatedDependencyGroupId,
                                relatedDependency.getArtifactId(),
                                relatedDependencyVersion),
                        dependencyCache,
                        visit);
                if (resolvedDependency != null) {
                    resolvedRelatedDependencies.add(resolvedDependency);
                }
            } else {
                resolvedRelatedDependencies.add(
                        new ResolvedDependency(relatedDependencyGroupId, relatedDependency.getArtifactId(), relatedDependency.getVersion(),
                                Collections.emptyList(),
                                Collections.emptyList())
                );
            }
        }

        ResolvedDependency resolvedDependency = new ResolvedDependency(groupId, artifactId, version,
                licenses,
                resolvedRelatedDependencies);

        dependencyCache.put(dependency, resolvedDependency);
        return resolvedDependency;

    }

    private String getActualVersion(Dependency dependency,
                                    Map<String, Dependency> dependenciesOfDependencyManagement,
                                    Map<String, String> properties) {

        String version = dependency.getVersion();

        if (StringUtils.hasText(dependency.getVersion())) {
            version = version.trim();

            if (version.startsWith("${") && version.endsWith("}")) {
                String versionVariable = version.replace("$", "").replace("{", "").replace("}", "");
                if (properties.containsKey(versionVariable)) {
                    return properties.get(versionVariable);
                }
            }
        } else { // White Space or Empty or null

            String key = dependency.getGroupId() + ":" + dependency.getArtifactId();

            if (dependenciesOfDependencyManagement.containsKey(key)) {
                Dependency dependencyOfDependencyManagement = dependenciesOfDependencyManagement.get(key);

                version = dependencyOfDependencyManagement.getVersion();
                version = version.trim();

                if (version.startsWith("${") && version.endsWith("}")) {
                    String versionVariable = version.replace("$", "").replace("{", "").replace("}", "");
                    if (properties.containsKey(versionVariable)) {
                        return properties.get(versionVariable);
                    }
                }
            }
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

                Element groupIdElement = e.selectFirst("groupId");
                Element artifactIdElement = e.selectFirst("artifactId");
                Element versionElement = e.selectFirst("version");

                if (groupIdElement != null) {
                    groupId = groupIdElement.text();
                }

                if (artifactIdElement != null) {
                    artifactId = artifactIdElement.text();
                }

                if (versionElement != null) {
                    version = versionElement.text();
                }

                Element scopeElement = e.selectFirst("scope");
                if (scopeElement == null) {
                    if (StringUtils.hasText(groupId) && StringUtils.hasText(artifactId)) {
                        dependencies.add(new Dependency(
                                groupId,
                                artifactId,
                                version));
                    }
                } else {
                    String scope = scopeElement.text();

                    //if ("compile".equals(scope) || "runtime".equals(scope)) {
                    if (true) {
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

                Element groupIdElement = e.selectFirst("groupId");
                Element artifactIdElement = e.selectFirst("artifactId");
                Element versionElement = e.selectFirst("version");

                if (groupIdElement != null) {
                    groupId = groupIdElement.text();
                }

                if (artifactIdElement != null) {
                    artifactId = artifactIdElement.text();
                }

                if (versionElement != null) {
                    version = versionElement.text();
                }

                if (StringUtils.hasText(groupId) && StringUtils.hasText(artifactId)) {
                    dependencies.put(groupId + ":" + artifactId, new Dependency(groupId, artifactId, version));
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
