package com.customersupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@Component
class KnowledgeBaseIngestor implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseIngestor.class);

    private final VectorStore vectorStore;

    KnowledgeBaseIngestor(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!vectorStore.similaritySearch(SearchRequest.builder().query("support").topK(1).build()).isEmpty()) {
            log.info("Knowledge base already seeded, skipping ingestion");
            return;
        }

        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources("classpath:rag-docs/*.md");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        TokenTextSplitter splitter = new TokenTextSplitter();
        for (Resource resource : resources) {
            MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, MarkdownDocumentReaderConfig.defaultConfig());
            List<Document> chunks = splitter.apply(reader.get());
            vectorStore.add(chunks);
            log.info("Ingested {} chunks from {}", chunks.size(), resource.getFilename());
        }
    }
}
