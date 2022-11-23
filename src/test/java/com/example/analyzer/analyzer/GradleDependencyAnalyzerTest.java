package com.example.analyzer.analyzer;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

@Slf4j
@SpringBootTest
class GradleDependencyAnalyzerTest {


    @Autowired
    GradleDependencyAnalyzer analyzer;

    @Test
    void analyze() {


        List<Dependency> dependencies = Arrays.asList(

                //new Dependency("org.eclipse.jgit", "org.eclipse.jgit", "6.3.0.202209071007-r"),
                new Dependency("org.jsoup", "jsoup", "1.15.3")
                //new Dependency("org.apache.httpcomponents", "httpclient", "4.5.13"),
                //new Dependency("junit", "junit", "4.13.2")
        );


        for (Dependency dependency : dependencies) {
            log.info("============================================");
            log.info("Dependency={}", dependency);
            ResolvedDependency resolvedDependency = analyzer.resolve(dependency);

            print(resolvedDependency, 1);
            log.info("============================================");

        }



    }

    void print(ResolvedDependency resolvedDependency, int depth) {
        if (resolvedDependency != null) {
            for (int i = 0; i < depth; i++) {
                if (i == depth - 1) {
                    System.out.print("+--- ");
                } else {
                    System.out.print("|    ");
                }
            }
            System.out.println(resolvedDependency);
            for (ResolvedDependency relatedDependency : resolvedDependency.getRelatedDependencies()) {
                print(relatedDependency, depth + 1);
            }
        }
    }
}