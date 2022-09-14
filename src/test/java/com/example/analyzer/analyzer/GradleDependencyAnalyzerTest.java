package com.example.analyzer.analyzer;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

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
                //new Dependency("junit", "junit", "4.13.2")
        );


        for (Dependency dependency : dependencies) {
            log.info("============================================");
            log.info("Dependency={}", dependency);
            Map<String, Dependency> dependencyMap = new HashMap<>();
            analyzer.get(dependencyMap, dependency);

            for (Dependency value : dependencyMap.values()) {
                log.info("{}", value.getInformation());
            }

            log.info("============================================");

        }



    }

}