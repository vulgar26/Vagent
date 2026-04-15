package com.vagent.kb;

import com.vagent.kb.dto.KbChunkIndexRow;
import com.vagent.kb.dto.RetrieveHit;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

/**
 * 内嵌 Lucene BM25：把用户下所有 chunk 构建成倒排索引，并支持按 query 搜索 TopK。
 * <p>
 * 注意：这是“第二检索系统里程碑”的最小实现，索引更新策略由上层决定（当前先做按需重建/缓存）。</p>
 */
final class LuceneBm25Index implements Closeable {

    private static final String F_CHUNK_ID = "chunk_id";
    private static final String F_DOCUMENT_ID = "document_id";
    private static final String F_CONTENT = "content";

    private final Directory directory;
    private final Analyzer analyzer;
    private final IndexSearcher searcher;

    private LuceneBm25Index(Directory directory, Analyzer analyzer, IndexSearcher searcher) {
        this.directory = directory;
        this.analyzer = analyzer;
        this.searcher = searcher;
    }

    static LuceneBm25Index build(List<KbChunkIndexRow> rows) {
        try {
            Analyzer analyzer = new StandardAnalyzer();
            Directory dir = new ByteBuffersDirectory();
            IndexWriterConfig cfg = new IndexWriterConfig(analyzer);
            try (IndexWriter w = new IndexWriter(dir, cfg)) {
                if (rows != null) {
                    for (KbChunkIndexRow r : rows) {
                        if (r == null || r.getChunkId() == null || r.getChunkId().isBlank()) {
                            continue;
                        }
                        String content = r.getContent() == null ? "" : r.getContent();
                        Document d = new Document();
                        d.add(new StringField(F_CHUNK_ID, r.getChunkId(), org.apache.lucene.document.Field.Store.YES));
                        d.add(new StringField(F_DOCUMENT_ID, r.getDocumentId() == null ? "" : r.getDocumentId(), org.apache.lucene.document.Field.Store.YES));
                        // content 既参与检索也要可回传 snippet（因此 Store=YES）
                        d.add(new TextField(F_CONTENT, content, org.apache.lucene.document.Field.Store.YES));
                        // 额外存一个长度字段，便于未来做截断/调试（不参与检索）
                        d.add(new StoredField("content_len", content.length()));
                        w.addDocument(d);
                    }
                }
                w.commit();
            }
            DirectoryReader reader = DirectoryReader.open(dir);
            IndexSearcher searcher = new IndexSearcher(reader);
            return new LuceneBm25Index(dir, analyzer, searcher);
        } catch (Exception e) {
            throw new IllegalStateException("failed to build lucene bm25 index", e);
        }
    }

    List<RetrieveHit> search(String queryText, int topK) {
        if (topK <= 0) {
            return List.of();
        }
        String q = queryText == null ? "" : queryText.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        try {
            QueryParser parser = new QueryParser(F_CONTENT, analyzer);
            Query query = parser.parse(QueryParser.escape(q));
            TopDocs td = searcher.search(query, topK);
            if (td == null || td.scoreDocs == null || td.scoreDocs.length == 0) {
                return List.of();
            }
            ArrayList<RetrieveHit> out = new ArrayList<>(td.scoreDocs.length);
            for (ScoreDoc sd : td.scoreDocs) {
                Document d = searcher.doc(sd.doc);
                String chunkId = d.get(F_CHUNK_ID);
                String docId = d.get(F_DOCUMENT_ID);
                String content = d.get(F_CONTENT);
                float score = sd.score;
                RetrieveHit h = new RetrieveHit();
                h.setChunkId(chunkId);
                h.setDocumentId(docId);
                h.setContent(content);
                // 伪距离：越小越相关，便于与向量 distance 命名统一（RRF 仍以排序为准）
                h.setDistance(1.0 / (1.0 + Math.max(0.0, score)));
                out.add(h);
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("failed to search lucene bm25", e);
        }
    }

    @Override
    public void close() {
        try {
            searcher.getIndexReader().close();
        } catch (Exception ignored) {
        }
        try {
            analyzer.close();
        } catch (Exception ignored) {
        }
        try {
            directory.close();
        } catch (Exception ignored) {
        }
    }
}

