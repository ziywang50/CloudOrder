package com.highvia.productservice.repository;

import com.highvia.productservice.document.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProductElasticsearchRepository extends ElasticsearchRepository<ProductDocument, String> {
    List<ProductDocument> findByNameContaining(String keyword);
    List<ProductDocument> findByNameContainingOrDescriptionContaining(String keyword, String description);
}
