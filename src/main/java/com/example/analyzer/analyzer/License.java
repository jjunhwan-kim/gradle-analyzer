package com.example.analyzer.analyzer;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class License {
    private final String name;
    private final String url;
    private final String content;

    @Override
    public String toString() {
        return name;
    }
}
