package com.example;


import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import org.apache.lucene.analysis.ngram.NGramTokenizer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class ngramIndexer {

    // per-field edge-ngram analyzer factory
    private static Analyzer createNGramAnalyzer(int minGram, int maxGram) {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tokenizer = new NGramTokenizer(minGram, maxGram);
                TokenStream ts = new LowerCaseFilter(tokenizer);
                return new TokenStreamComponents(tokenizer, ts);
            }
        };
    }

    public static void buildIndex(String jsonFolderPath, String indexPath) throws IOException {
        // 默认 analyzer
        Analyzer defaultAnalyzer = new StandardAnalyzer();

        // 为特定字段使用 ngram 
        Map<String, Analyzer> perField = new HashMap<>();
        // authors/affiliations/address 使用 n-gram（可根据需要调整 min/max）
        perField.put("title", createNGramAnalyzer(2, 20));
        perField.put("authors", createNGramAnalyzer(2, 20));
        perField.put("affiliations", createNGramAnalyzer(2, 20));
        perField.put("address", createNGramAnalyzer(2, 20));

        Analyzer analyzer = new PerFieldAnalyzerWrapper(defaultAnalyzer, perField);

        Directory dir = FSDirectory.open(Paths.get(indexPath));
        IndexWriterConfig cfg = new IndexWriterConfig(analyzer);
        try (IndexWriter writer = new IndexWriter(dir, cfg)) {
            File folder = new File(jsonFolderPath);
            File[] files = folder.listFiles((d, name) -> name.toLowerCase().endsWith(".json"));
            if (files == null) {
                System.out.println("No JSON files found in " + jsonFolderPath);
                return;
            }
            for (File f : files) {
                try {
                    String content = new String(Files.readAllBytes(f.toPath()));
                    JSONObject json = new JSONObject(content);
                    String title = json.optString("title", "");
                    String publicationDate = json.optString("publication_date", "");
                    String fullText = json.optString("full_text", "");

                    // authors 拼接
                    StringBuilder authorsSb = new StringBuilder();
                    JSONArray authorsArr = json.optJSONArray("authors");
                    if (authorsArr != null) {
                        for (int i = 0; i < authorsArr.length(); i++) {
                            JSONObject a = authorsArr.optJSONObject(i);
                            if (a != null) {
                                String name = a.optString("full_name", "");
                                if (!name.isBlank()) {
                                    if (authorsSb.length() > 0) authorsSb.append(" ");
                                    authorsSb.append(name);
                                }
                                // affiliations 合并（取作者中第一个的机构也追加到 affiliations 字段）
                            }
                        }
                    }

                    // affiliations / address — 合并所有作者的第一个 affiliation 的 organization / country
                    StringBuilder affSb = new StringBuilder();
                    StringBuilder addrSb = new StringBuilder();
                    if (authorsArr != null) {
                        for (int i = 0; i < authorsArr.length(); i++) {
                            JSONObject a = authorsArr.optJSONObject(i);
                            if (a == null) continue;
                            JSONArray affs = a.optJSONArray("affiliations");
                            if (affs != null && affs.length() > 0) {
                                JSONObject aff = affs.optJSONObject(0);
                                if (aff != null) {
                                    String org = aff.optString("organization", "");
                                    if (!org.isBlank()) {
                                        if (affSb.length() > 0) affSb.append(" ");
                                        affSb.append(org);
                                    }
                                    JSONObject address = aff.optJSONObject("address");
                                    if (address != null) {
                                        String country = address.optString("country", "");
                                        String city = address.optString("city", "");
                                        if (!city.isBlank()) {
                                            if (addrSb.length() > 0) addrSb.append(" ");
                                            addrSb.append(city);
                                        }
                                        if (!country.isBlank()) {
                                            if (addrSb.length() > 0) addrSb.append(" ");
                                            addrSb.append(country);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Document doc = new Document();
                    doc.add(new TextField("title", title, Field.Store.YES));
                    doc.add(new TextField("authors", authorsSb.toString(), Field.Store.YES));
                    doc.add(new TextField("publication_date", publicationDate, Field.Store.YES));
                    doc.add(new TextField("affiliations", affSb.toString(), Field.Store.YES));
                    doc.add(new TextField("address", addrSb.toString(), Field.Store.YES));
                    doc.add(new TextField("full_text", fullText, Field.Store.YES));
                    doc.add(new org.apache.lucene.document.StoredField("json_filename", f.getName()));

                    writer.addDocument(doc);

                } catch (JSONException je) {
                    System.err.println("Skip invalid JSON: " + f.getName() + " -> " + je.getMessage());
                } catch (Exception ex) {
                    System.err.println("Error indexing " + f.getName() + ": " + ex.getMessage());
                }
            }
            writer.commit();
            System.out.println("ngram index built to " + indexPath);
        } finally {
            analyzer.close();
        }
    }

    public static void main(String[] args) {
        try {
            String jsonDir = "D:/Codes/IR-system/output";
            String indexDir = "D:/Codes/IR-system/ngram_index";
            buildIndex(jsonDir, indexDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }





    
}
