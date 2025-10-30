package com.example;

import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.search.spell.LuceneDictionary;
import org.apache.lucene.search.spell.PlainTextDictionary;
import org.apache.lucene.analysis.ngram.NGramTokenizer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;


import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.directory.SearchResult;

import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.SimpleSpanFragmenter;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;





@RestController
@RequestMapping("/search")
public class Controller {
    
    @GetMapping
    public java.util.Map<String, Object> search(
        @RequestParam("q") String queryStr,
        @RequestParam(value = "fields", required = false) List<String> fields,
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "size", defaultValue = "12") int size,
        @RequestParam(value = "startYear", required = false) Integer startYear,
        @RequestParam(value = "endYear", required = false) Integer endYear
    ) {

        List<SearchResult> results = new ArrayList<>();
        int total = 0;
        try {
            FSDirectory dir = FSDirectory.open(Paths.get("D:/Codes/IR-system/new_index"));
            DirectoryReader reader = DirectoryReader.open(dir);
            IndexSearcher searcher = new IndexSearcher(reader);

            // 默认搜索所有字段
            String[] allFields = {"title", "authors", "publication_date", "affiliations", "address", "full_text"};
            String[] searchFields = (fields == null || fields.isEmpty()) ? allFields : fields.toArray(new String[0]);

            Analyzer analyzer = new StandardAnalyzer();
            MultiFieldQueryParser parser = new MultiFieldQueryParser(searchFields, analyzer);
            Query query = parser.parse(queryStr);


            int fetchMultiplier = 6;          // 可调：抓取 page*size*fetchMultiplier 个候选
            int maxFetch = 1000;              // 上限，避免一次抓太多
            int fetchCount = Math.min(page * size * fetchMultiplier, maxFetch);


            // 取前 page*size 条，保证能分页
            TopDocs topDocs = searcher.search(query, fetchCount);

            // 逐 doc 过滤符合年份范围的文档
/*            List<Document> matched = new ArrayList<>();
            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document doc = searcher.doc(sd.doc);
                if (startYear != null || endYear != null) {
                    int y = extractYear(doc.get("publication_date"));
                    if (y == 0) {
                        // 如果无法提取年份，可选择跳过或包含；这里我们跳过没有年份的文档
                        continue;
                    }
                    if (startYear != null && y < startYear) continue;
                    if (endYear != null && y > endYear) continue;
                }
                matched.add(doc);
            }

            total = matched.size(); 

            // 计算当前页的起止
            int start = (page - 1) * size;
            int end = Math.min(start + size, matched.size());

*/ 


            // 在没有年份过滤时，用 count 获取真实总数
            if (startYear == null && endYear == null) {
                total = Math.toIntExact(searcher.count(query)); // 精确总命中数
                // 仍按分页从 topDocs 取当前页（或用 searcher.search(query, page*size) 更小开销）
                int start = (page - 1) * size;
                int end = Math.min(start + size, topDocs.scoreDocs.length);
                for (int i = start; i < end; i++) {
                    Document doc = searcher.doc(topDocs.scoreDocs[i].doc);
                    // ... build result ...
                    String title = doc.get("title");
                    String fullText = doc.get("full_text");
                    String hlTitle = highlightField(analyzer, query, "title", title);
                    String hlFull = highlightField(analyzer, query, "full_text", fullText);

                    results.add(new SearchResult(
                            doc.get("title"),
                            doc.get("authors"),
                            doc.get("publication_date"),
                            doc.get("affiliations"),
                            doc.get("address"),
                            doc.get("full_text"),
                            doc.get("json_filename"),
                            hlTitle,
                            hlFull
                    ));
                }
                // 返回
            } else {
                // 有年份过滤，走后处理（见下）
                int batch = 1000;                 // 每次取多少候选（可调）
                List<Document> matchedAll = new ArrayList<>();
                int requested = batch;
                int processed = 0;

                while (true) {
                    TopDocs pageDocs = searcher.search(query, requested);
                    ScoreDoc[] sds = pageDocs.scoreDocs;
                    // 避免重复处理已处理的部分
                    for (int i = processed; i < sds.length; i++) {
                        Document doc = searcher.doc(sds[i].doc);
                        // 年份过滤逻辑
                        int y = extractYear(doc.get("publication_date"));
                        if (y == 0) continue;
                        if (startYear != null && y < startYear) continue;
                        if (endYear != null && y > endYear) continue;
                        matchedAll.add(doc);
                    }
                    processed = sds.length;
                    // 如果已经拿到所有命中（sds.length < requested）说明全部扫描完
                    if (sds.length < requested) break;
                    // 否则扩大请求数量再取下一批（注意开销）
                    requested = Math.min(requested + batch, 1000); // 总上限可设置
                    // 防止无限循环：如果 requested 超过某个阈值且仍没结束，可 break 并返回当前 matchedAll.size() 作为近似
                    if (requested > 2000) break;
                }
                // matchedAll 包含所有符合年份的 doc
                total = matchedAll.size();
                // 对分页返回 matchedAll 的子区间
                int start = (page - 1) * size;
                int end = Math.min(start + size, matchedAll.size());
            



                for (int i = start; i < end; i++) {
                    Document doc = matchedAll.get(i);

                    String title = doc.get("title");
                    String fullText = doc.get("full_text");
                    // 高亮：优先对 title 和 full_text 生成片段
                    String hlTitle = highlightField(analyzer, query, "title", title);
                    String hlFull = highlightField(analyzer, query, "full_text", fullText);

                    results.add(new SearchResult(
                            doc.get("title"),
                            doc.get("authors"),
                            doc.get("publication_date"),
                            doc.get("affiliations"),
                            doc.get("address"),
                            doc.get("full_text"),
                            doc.get("json_filename"),
                            hlTitle,
                            hlFull
                    ));
                }
            }


            reader.close();
            dir.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("results", results);
        map.put("total", total);
        return map;
    }

    

    public static class SearchResult {
        public String title;
        public String authors;
        public String publicationDate;
        public String affiliations;
        public String address;
        public String fullText;
        public String pdfFilename; // 新增
        public String highlightTitle; // 可选高亮标题（HTML）
        public String highlight; // 高亮片段（HTML）

        public SearchResult(String title, String authors, String publicationDate, String affiliations, String address, String fullText, String pdfFilename, String highlightTitle, String highlight) {
            this.title = title;
            this.authors = authors;
            this.publicationDate = publicationDate;
            this.affiliations = affiliations;
            this.address = address;
            this.fullText = fullText;
            this.pdfFilename = pdfFilename;
            this.highlightTitle = highlightTitle;
            this.highlight = highlight;
        }
    }

    // ---------- n-gram 分析器工厂 ----------
    private static Analyzer createNGramAnalyzer(final int minGram, final int maxGram) {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tokenizer = new NGramTokenizer(minGram, maxGram);
                TokenStream ts = new LowerCaseFilter(tokenizer);
                return new TokenStreamComponents(tokenizer, ts);
            }
        };
    }





    // ---------- 新增：基于 n-gram 索引的检索接口 ----------
    @GetMapping("/ngram")
    public java.util.Map<String, Object> ngramSearch(
        @RequestParam("q") String queryStr,
        @RequestParam(value = "fields", required = false) List<String> fields,
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "size", defaultValue = "12") int size,
        @RequestParam(value = "startYear", required = false) Integer startYear,
        @RequestParam(value = "endYear", required = false) Integer endYear
    ) {
        List<SearchResult> results = new ArrayList<>();
        int total = 0;

        //  n-gram 索引目录
        String indexPath = "D:/Codes/IR-system/ngram_index";

        // 默认搜索字段
        String[] allFields = {"title", "authors", "publication_date", "affiliations", "address", "full_text"};
        String[] searchFields = (fields == null || fields.isEmpty()) ? allFields : fields.toArray(new String[0]);

        // 为部分字段使用 n-gram analyzer，其余使用 StandardAnalyzer
        Analyzer defaultAnalyzer = new StandardAnalyzer();
        Map<String, Analyzer> perField = new HashMap<>();
        // 对 title/authors/affiliations/address 开启任意 n-gram（minGram=2,maxGram=20）
        perField.put("title", createNGramAnalyzer(2, 20));
        perField.put("authors", createNGramAnalyzer(2, 20));
        perField.put("affiliations", createNGramAnalyzer(2, 20));
        perField.put("address", createNGramAnalyzer(2, 20));

        Analyzer analyzer = new PerFieldAnalyzerWrapper(defaultAnalyzer, perField);

        try {
            FSDirectory dir = FSDirectory.open(Paths.get(indexPath));
            DirectoryReader reader = DirectoryReader.open(dir);
            IndexSearcher searcher = new IndexSearcher(reader);

            MultiFieldQueryParser parser = new MultiFieldQueryParser(searchFields, analyzer);
            Query query = parser.parse(queryStr);

/* 
            TopDocs topDocs = searcher.search(query, page * size);
            total = Math.toIntExact(topDocs.totalHits.value);

            int start = (page - 1) * size;
            int end = Math.min(start + size, topDocs.scoreDocs.length);

            for (int i = start; i < end; i++) {
                Document doc = searcher.doc(topDocs.scoreDocs[i].doc);

                String title = doc.get("title");
                String fullText = doc.get("full_text");

                String hlTitle = highlightField(analyzer, query, "title", title);
                String hlFull = highlightField(analyzer, query, "full_text", fullText);

                results.add(new SearchResult(
                        doc.get("title"),
                        doc.get("authors"),
                        doc.get("publication_date"),
                        doc.get("affiliations"),
                        doc.get("address"),
                        doc.get("full_text"),
                        doc.get("json_filename"),
                        hlTitle,
                        hlFull
                ));
            }

            reader.close();
            dir.close();
*/
            if (startYear == null && endYear == null) {
                // 无年份过滤：准确计数并分页返回
                total = Math.toIntExact(searcher.count(query));
                TopDocs topDocs = searcher.search(query, page * size);
                int start = (page - 1) * size;
                int end = Math.min(start + size, topDocs.scoreDocs.length);
                for (int i = start; i < end; i++) {
                    Document doc = searcher.doc(topDocs.scoreDocs[i].doc);
                    String title = doc.get("title");
                    String fullText = doc.get("full_text");
                    String hlTitle = highlightField(analyzer, query, "title", title);
                    String hlFull = highlightField(analyzer, query, "full_text", fullText);

                    results.add(new SearchResult(
                            doc.get("title"),
                            doc.get("authors"),
                            doc.get("publication_date"),
                            doc.get("affiliations"),
                            doc.get("address"),
                            doc.get("full_text"),
                            doc.get("json_filename"),
                            hlTitle,
                            hlFull
                    ));
                }
            } else {
                // 有年份过滤：批量扫描所有匹配并筛选（不重建索引）
                int batch = 1000;
                List<Document> matchedAll = new ArrayList<>();
                int requested = batch;
                int processed = 0;

                while (true) {
                    TopDocs pageDocs = searcher.search(query, requested);
                    ScoreDoc[] sds = pageDocs.scoreDocs;
                    for (int i = processed; i < sds.length; i++) {
                        Document doc = searcher.doc(sds[i].doc);
                        int y = extractYear(doc.get("publication_date"));
                        if (y == 0) continue;
                        if (startYear != null && y < startYear) continue;
                        if (endYear != null && y > endYear) continue;
                        matchedAll.add(doc);
                    }
                    processed = sds.length;
                    if (sds.length < requested) break;
                    requested = Math.min(requested + batch, 100000); // 可调总上限
                    if (requested > 200000) break; // 安全阈值
                }

                total = matchedAll.size();
                int start = (page - 1) * size;
                int end = Math.min(start + size, matchedAll.size());
                for (int i = start; i < end; i++) {
                    Document doc = matchedAll.get(i);
                    String title = doc.get("title");
                    String fullText = doc.get("full_text");
                    String hlTitle = highlightField(analyzer, query, "title", title);
                    String hlFull = highlightField(analyzer, query, "full_text", fullText);

                    results.add(new SearchResult(
                            doc.get("title"),
                            doc.get("authors"),
                            doc.get("publication_date"),
                            doc.get("affiliations"),
                            doc.get("address"),
                            doc.get("full_text"),
                            doc.get("json_filename"),
                            hlTitle,
                            hlFull
                    ));
                }
            }

            reader.close();
            dir.close();


        
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            analyzer.close();
        }

        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("results", results);
        map.put("total", total);
        return map;
    }



    @GetMapping("/spell")
    public java.util.Map<String, Object> spell(
        @RequestParam("q") String queryStr,
        @RequestParam(value = "max", defaultValue = "1") int maxSuggestions
    ) {
        java.util.Map<String, Object> ret = new java.util.HashMap<>();
        try {
            // 主索引路径
            java.nio.file.Path mainIndex = Paths.get("D:/Codes/IR-system/new_index");
            java.nio.file.Path spellIndex = Paths.get("D:/Codes/IR-system/spell_index_new");
            java.nio.file.Path dictFile = Paths.get("D:/Codes/IR-system/words.txt"); // <- 放标准词表的位置，一行一个词



            
            /*if (Files.exists(dictFile)) {
                try (SpellChecker sc = new SpellChecker(FSDirectory.open(spellIndex))) {
                    sc.indexDictionary(new PlainTextDictionary(dictFile), new IndexWriterConfig(new StandardAnalyzer()), true);
                }
            } else {

                if (!Files.exists(spellIndex) || Files.list(spellIndex).findAny().isEmpty()) {
                    try (IndexReader reader = DirectoryReader.open(FSDirectory.open(mainIndex))) {
                        SpellChecker sc = new SpellChecker(FSDirectory.open(spellIndex));
                        sc.indexDictionary(new LuceneDictionary(reader, "full_text"), new IndexWriterConfig(new StandardAnalyzer()), true);
                        sc.close();
                    }
                }
            }
    */


        

            if (Files.exists(dictFile)) {
                // 只在 spellIndex 不存在/为空 或 dict 比 spellIndex 更新时重建
                boolean needBuild = dirIsEmpty(spellIndex);
                if (!needBuild) {
                    long dictTime = Files.getLastModifiedTime(dictFile).toMillis();
                    long idxMax = Files.list(spellIndex)
                                    .mapToLong(p -> {
                                        try { return Files.getLastModifiedTime(p).toMillis(); }
                                        catch (Exception ex) { return 0L; }
                                    }).max().orElse(0L);
                    if (dictTime > idxMax) needBuild = true;
                }
                if (needBuild) {
                    try (SpellChecker sc = new SpellChecker(FSDirectory.open(spellIndex))) {
                        sc.indexDictionary(new PlainTextDictionary(dictFile), 
                        new IndexWriterConfig(new StandardAnalyzer()), true);
                    }
                }
            } else {
                if (dirIsEmpty(spellIndex)) {
                    try (IndexReader reader = DirectoryReader.open(FSDirectory.open(mainIndex))) {
                        try (SpellChecker sc = new SpellChecker(FSDirectory.open(spellIndex))) {
                            sc.indexDictionary(new LuceneDictionary(reader, "full_text"), new IndexWriterConfig(new StandardAnalyzer()), true);
                        }
                    }
                }
            }

            // 打开 spell checker
            try (SpellChecker sc = new SpellChecker(FSDirectory.open(spellIndex))) {
                // 分词输入（用 StandardAnalyzer 保证和索引一致）
                java.util.List<String> tokens = new java.util.ArrayList<>();
                try (org.apache.lucene.analysis.Analyzer ana = new StandardAnalyzer()) {
                    try (org.apache.lucene.analysis.TokenStream ts = ana.tokenStream("", queryStr)) {
                        org.apache.lucene.analysis.tokenattributes.CharTermAttribute attr = ts.addAttribute(org.apache.lucene.analysis.tokenattributes.CharTermAttribute.class);
                        ts.reset();
                        while (ts.incrementToken()) {
                            String t = attr.toString();
                            if (!t.isBlank()) tokens.add(t);
                        }
                        ts.end();
                    }
                }

                java.util.List<String> correctedTokens = new java.util.ArrayList<>();
                java.util.Map<String, String> perToken = new java.util.HashMap<>();
                for (String token : tokens) {
                    if (token.length() <= 1) { // 太短的不纠错
                        correctedTokens.add(token);
                        perToken.put(token, token);
                        continue;
                    }
                    String[] sug = sc.suggestSimilar(token, maxSuggestions);
                    if (sug != null && sug.length > 0 && !sug[0].equalsIgnoreCase(token)) {
                        correctedTokens.add(sug[0]);
                        perToken.put(token, sug[0]);
                    } else {
                        correctedTokens.add(token);
                        perToken.put(token, token);
                    }
                }
                String corrected = String.join(" ", correctedTokens);
                ret.put("original", queryStr);
                ret.put("corrected", corrected);
                ret.put("per_token", perToken);
            }

        } catch (Exception e) {
            e.printStackTrace();
            ret.put("error", e.getMessage());
        }
        return ret;
    }


    boolean dirIsEmpty(java.nio.file.Path dir) throws java.io.IOException {
                return Files.notExists(dir) || Files.list(dir).findAny().isEmpty();
    }
    
    private static String highlightField(Analyzer analyzer, Query query, String field, String text) {
        if (text == null || text.isBlank()) return null;
        try {
            SimpleHTMLFormatter formatter = new SimpleHTMLFormatter("<mark>", "</mark>");
            QueryScorer scorer = new QueryScorer(query, field);
            Highlighter highlighter = new Highlighter(formatter, scorer);
            highlighter.setTextFragmenter(new SimpleSpanFragmenter(scorer, 100)); // 每片段大约100字符
            TokenStream ts = analyzer.tokenStream(field, text);
            String frag = highlighter.getBestFragment(ts, text);
            if (frag != null && !frag.isBlank()) return frag;
        } catch (Exception e) {
            // ignore highlighting errors
            e.printStackTrace();
        }
        return null;
    }

    private static int extractYear(String dateStr) {
        if (dateStr == null) return 0;
        String s = dateStr.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{4})").matcher(s);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ex) { return 0; }
        }
        return 0;
    }



    
}






