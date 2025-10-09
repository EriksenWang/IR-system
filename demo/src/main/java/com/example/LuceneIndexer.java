package com.example;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
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
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;




public class LuceneIndexer {
    private IndexWriter writer;

     // 构造函数：初始化 IndexWriter
    public LuceneIndexer(String indexPath) throws IOException {
        Directory dir = FSDirectory.open(Paths.get(indexPath));
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        writer = new IndexWriter(dir, config);
    }

    // 构建索引的方法
    public void indexDocumentsFromJsonFolder(String jsonFolderPath) throws IOException {
        File folder = new File(jsonFolderPath);
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));

        if (files == null) {
            System.out.println("No JSON files found in the directory.");
            return;
        }

        for (File file : files) {
            try {
                JSONObject json = new JSONObject(new String(Files.readAllBytes(file.toPath())));
                String title = json.getString("title");
                String publicationDate = json.getString("publication_date");
                JSONArray authorsArray = json.getJSONArray("authors");
                StringBuilder authors = new StringBuilder();
                for (int i = 0; i < authorsArray.length(); i++) {
                    authors.append(authorsArray.getJSONObject(i).getString("full_name")).append(" ");
                }
                String affiliations = "";
                String address = "";
                if (authorsArray.length() > 0) {
                    JSONObject firstAuthor = authorsArray.getJSONObject(0);
                    if (firstAuthor.has("affiliations")) {
                        JSONArray affils = firstAuthor.getJSONArray("affiliations");
                        if (affils.length() > 0) {
                            JSONObject affiliation = affils.getJSONObject(0);
                            affiliations = affiliation.getString("organization");
                            if (affiliation.has("address")) {
                                JSONObject addr = affiliation.getJSONObject("address");
                                address = addr.optString("country", "");  // 使用 optString 进行安全访问
                            }
                        }
                    }
                }
                String fullText = json.getString("full_text");

                // 将解析出来的字段添加到索引中
                indexDocument(title, authors.toString(), publicationDate, affiliations, address, fullText);
            } catch (JSONException e) {
                System.err.println("Skipping invalid JSON file: " + file.getName() + " - " + e.getMessage());
            }
        }
    }

    // 创建倒排索引的文档
    public void indexDocument(String title, String authors, String publicationDate, String affiliations, String address, String fullText) throws IOException {
        Document doc = new Document();
        doc.add(new TextField("title", title, Field.Store.YES));
        doc.add(new TextField("authors", authors, Field.Store.YES));  // 将所有作者的名字合并成一个字段
        doc.add(new TextField("publication_date", publicationDate, Field.Store.YES));
        doc.add(new TextField("affiliations", affiliations, Field.Store.YES));  // 机构信息
        doc.add(new TextField("address", address, Field.Store.YES));  // 地址信息
        doc.add(new TextField("full_text", fullText, Field.Store.YES));  // 论文内容
        writer.addDocument(doc);
    }

    // 关闭 IndexWriter
    public void close() throws IOException {
        writer.close();
    }

    public static void main(String[] args) {
        try {
            LuceneIndexer indexer = new LuceneIndexer("D:/Codes/IR-system/index");
            indexer.indexDocumentsFromJsonFolder("D:/Codes/IR-system/output");
            indexer.close();
            System.out.println("索引创建成功！");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
