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
    public List<SearchResult> search(
        @RequestParam("q") String queryStr,
        @RequestParam(value = "fields", required = false) List<String> fields
    ) {

        List<SearchResult> results = new ArrayList<>();
        try {
            FSDirectory dir = FSDirectory.open(Paths.get("D:/Codes/IR-system/index"));
            DirectoryReader reader = DirectoryReader.open(dir);
            IndexSearcher searcher = new IndexSearcher(reader);

            // 默认搜索所有字段
            String[] allFields = {"title", "authors", "publication_date", "affiliations", "address", "full_text"};
            String[] searchFields = (fields == null || fields.isEmpty()) ? allFields : fields.toArray(new String[0]);

            MultiFieldQueryParser parser = new MultiFieldQueryParser(searchFields, new StandardAnalyzer());
            Query query = parser.parse(queryStr);

            TopDocs topDocs = searcher.search(query, 10); // 返回前10条
            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document doc = searcher.doc(sd.doc);
                results.add(new SearchResult(
                        doc.get("title"),
                        doc.get("authors"),
                        doc.get("publication_date"),
                        doc.get("affiliations"),
                        doc.get("address"),
                        doc.get("full_text")
                ));
            }
            reader.close();
            dir.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }

    

    public static class SearchResult {
        public String title;
        public String authors;
        public String publicationDate;
        public String affiliations;
        public String address;
        public String fullText;

        public SearchResult(String title, String authors, String publicationDate, String affiliations, String address, String fullText) {
            this.title = title;
            this.authors = authors;
            this.publicationDate = publicationDate;
            this.affiliations = affiliations;
            this.address = address;
            this.fullText = fullText;
        }
    }
    
}






