package com.example;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;



@RestController
@RequestMapping("/search")
public class Controller {
    
    @GetMapping
    public java.util.Map<String, Object> search(
        @RequestParam("q") String queryStr,
        @RequestParam(value = "fields", required = false) List<String> fields,
        @RequestParam(value = "page", defaultValue = "1") int page,
        @RequestParam(value = "size", defaultValue = "12") int size
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

            MultiFieldQueryParser parser = new MultiFieldQueryParser(searchFields, new StandardAnalyzer());
            Query query = parser.parse(queryStr);


            // 取前 page*size 条，保证能分页
            TopDocs topDocs = searcher.search(query, page * size);
            total = Math.toIntExact(topDocs.totalHits.value);

            // 计算当前页的起止
            int start = (page - 1) * size;
            int end = Math.min(start + size, topDocs.scoreDocs.length);



            /*TopDocs topDocs = searcher.search(query, 10); 
            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document doc = searcher.doc(sd.doc);
                results.add(new SearchResult(
                        doc.get("title"),
                        doc.get("authors"),
                        doc.get("publication_date"),
                        doc.get("affiliations"),
                        doc.get("address"),
                        doc.get("full_text"),
                        doc.get("json_filename") // 新增
                ));
            }*/

            for (int i = start; i < end; i++) {
                Document doc = searcher.doc(topDocs.scoreDocs[i].doc);
                results.add(new SearchResult(
                        doc.get("title"),
                        doc.get("authors"),
                        doc.get("publication_date"),
                        doc.get("affiliations"),
                        doc.get("address"),
                        doc.get("full_text"),
                        doc.get("json_filename")
                ));
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

        public SearchResult(String title, String authors, String publicationDate, String affiliations, String address, String fullText, String pdfFilename) {
            this.title = title;
            this.authors = authors;
            this.publicationDate = publicationDate;
            this.affiliations = affiliations;
            this.address = address;
            this.fullText = fullText;
            this.pdfFilename = pdfFilename;
        }
    }
    
}






