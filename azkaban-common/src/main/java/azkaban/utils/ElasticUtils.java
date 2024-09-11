package azkaban.utils;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ElasticUtils {

  private static final Logger logger = LoggerFactory.getLogger(ElasticUtils.class);

  public static final RequestOptions COMMON_OPTIONS;

  static {
    RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();
    COMMON_OPTIONS = builder.build();
  }

  public static RestHighLevelClient esRestClient(List<String> addressList) {

    HttpHost[] arr = new HttpHost[addressList.size()];
    for (int i = 0; i < addressList.size(); i++) {
      String[] address = addressList.get(i).split(":");
      arr[i] = new HttpHost(address[0], Integer.parseInt(address[1]), "http");
    }
    RestClientBuilder builder = RestClient.builder(arr);
    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        AuthScope.ANY, new UsernamePasswordCredentials("elastic", "elastic"));
    builder.setHttpClientConfigCallback(f -> f.setDefaultCredentialsProvider(credentialsProvider));
    RestHighLevelClient restClient = new RestHighLevelClient(builder);
    return restClient;
  }

  public static List<String> getEsLog(String index, List<String> addressList, int start,
      int limit) {

    List<String> list = new ArrayList<>();
    RestHighLevelClient restHighLevelClient = esRestClient(addressList);
    try {
      SearchRequest request = new SearchRequest();
      request.indices(index);
      SearchSourceBuilder ssb = new SearchSourceBuilder();
      ssb.from(start).size(limit).sort("@timestamp", SortOrder.ASC);
      ssb.fetchSource(new String[]{"message"}, new String[]{});
      BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
      ssb.query(boolQueryBuilder);
      request.source(ssb);

      SearchResponse response = restHighLevelClient.search(request, COMMON_OPTIONS);
      RestStatus status = response.status();
      if (RestStatus.OK.equals(status)) {
        SearchHit[] searchHits = response.getHits().getHits();
        for (SearchHit hit : searchHits) {
          Map<String, Object> sourceAsMap = hit.getSourceAsMap();
          list.add(hit.getSourceAsMap().get("message").toString());
        }
      }

    } catch (Exception e) {
      logger.error("get es log error", e);
    } finally {
      try {
        restHighLevelClient.close();
      } catch (IOException e) {
        logger.error("close es client error", e);
      }
    }
    return list;
  }

}
